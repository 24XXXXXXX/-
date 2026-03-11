package com.communitysport.video.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.upload.service.UploadService;
import com.communitysport.video.dto.CoachVideoCreateRequest;
import com.communitysport.video.dto.CoachVideoDetailResponse;
import com.communitysport.video.dto.CoachVideoListItem;
import com.communitysport.video.dto.CoachVideoPageResponse;
import com.communitysport.video.dto.CoachVideoStatusRequest;
import com.communitysport.video.dto.CoachVideoUpdateRequest;
import com.communitysport.video.dto.CoachVideoUploadResponse;
import com.communitysport.video.dto.VideoPurchasePageResponse;
import com.communitysport.video.dto.VideoPurchaseListItem;
import com.communitysport.video.dto.VideoPurchaseResponse;
import com.communitysport.video.entity.CoachVideo;
import com.communitysport.video.entity.CoachVideoPurchase;
import com.communitysport.video.mapper.CoachVideoMapper;
import com.communitysport.video.mapper.CoachVideoPurchaseMapper;
import com.communitysport.wallet.service.WalletService;

@Service
public class CoachVideoService {

    // 核心服务：教练视频（CoachVideo）与购买记录（CoachVideoPurchase）的业务编排层。
    //
    // 该类同时服务两类入口：
    // - 教练端：上传视频/封面、新建/编辑/上下架、查看“我的视频”
    // - 用户端：公开目录/详情、购买（钱包扣款）、查看“我的购买记录”
    //
    // 关键业务点：
    // - 安全边界：
    //   - 是否登录、是否本人视频（coachUserId 匹配）由各方法显式校验
    //   - 付费视频的真正播放地址（videoUrl）是否返回，由 publicDetail 的 purchased 判定控制
    // - 一致性：purchase() 使用事务包裹“扣款 + 写购买记录 + 给教练入账”
    // - 幂等/并发：购买表通常有 (user_id, video_id) 唯一约束；并发购买通过 DuplicateKeyException 做补偿
    // - 性能：列表/购买记录的 DTO 组装使用批量查询 + Map 聚合，避免 N+1

    private final CoachVideoMapper coachVideoMapper;

    private final CoachVideoPurchaseMapper coachVideoPurchaseMapper;

    private final SysUserMapper sysUserMapper;

    private final WalletService walletService;

    private final UploadService uploadService;

    public CoachVideoService(
            CoachVideoMapper coachVideoMapper,
            CoachVideoPurchaseMapper coachVideoPurchaseMapper,
            SysUserMapper sysUserMapper,
            WalletService walletService,
            UploadService uploadService
    ) {
        this.coachVideoMapper = coachVideoMapper;
        this.coachVideoPurchaseMapper = coachVideoPurchaseMapper;
        this.sysUserMapper = sysUserMapper;
        this.walletService = walletService;
        this.uploadService = uploadService;
    }

    public CoachVideoUploadResponse uploadVideo(AuthenticatedUser principal, MultipartFile file) {
        // 上传视频文件（教练端）：
        // - 这里直接将文件写入到项目目录的 static/upload/video 下
        // - 返回可通过静态资源映射访问的 URL（/upload/video/{fileName}）
        //
        // 注意：
        // - 本方法不做“文件类型白名单”校验，仅保留原始扩展名（ext）并限制长度
        // - normalize + startsWith(dir) 是典型的路径穿越防护（防止构造 ../ 写出目录）
        requireUserId(principal);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (StringUtils.hasText(original)) {
            int idx = original.lastIndexOf('.');
            if (idx >= 0 && idx < original.length() - 1) {
                ext = original.substring(idx).toLowerCase();
            }
        }
        if (ext.length() > 10) {
            ext = "";
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = Paths.get("src", "main", "resources", "static", "upload", "video").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create video dir");
        }

        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid filename");
        }

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file");
        }

        CoachVideoUploadResponse resp = new CoachVideoUploadResponse();
        resp.setFileName(fileName);
        resp.setVideoUrl("/upload/video/" + fileName);
        return resp;
    }

    @Transactional
    public CoachVideoDetailResponse create(AuthenticatedUser principal, CoachVideoCreateRequest request) {
        // 教练新建视频元数据：
        // - title/videoUrl 必填；category/coverUrl/description 选填
        // - category 为空时默认“其他”
        // - price 为空默认为 0（免费）
        // - status 默认 ON_SALE（上架）
        //
        // 事务意义：该方法会写入 coach_video 主表；写入成功后再查询详情组装 DTO。
        Long coachUserId = requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getTitle()) || !StringUtils.hasText(request.getVideoUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title/videoUrl required");
        }

        String title = request.getTitle().trim();
        if (title.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title length must be <=100");
        }

        String category = StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : null;
        if (category != null && category.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category length must be <=50");
        }
        if (category == null) {
            category = "其他";
        }

        int price = request.getPrice() == null ? 0 : request.getPrice().intValue();
        if (price < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price invalid");
        }

        CoachVideo row = new CoachVideo();
        row.setCoachUserId(coachUserId);
        row.setTitle(title);
        row.setCategory(category);
        row.setPrice(price);
        row.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);
        row.setVideoUrl(request.getVideoUrl().trim());
        row.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        row.setStatus("ON_SALE");
        row.setCreatedAt(LocalDateTime.now());
        coachVideoMapper.insert(row);

        return myDetail(principal, row.getId());
    }

    @Transactional
    public CoachVideoDetailResponse update(AuthenticatedUser principal, Long id, CoachVideoUpdateRequest request) {
        // 教练编辑视频：
        // - 典型“部分更新”模型：request 中字段为 null 表示不修改
        // - 安全边界：只能编辑自己创建的视频（coachUserId 必须匹配）
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        CoachVideo row = coachVideoMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }
        if (!Objects.equals(row.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (request.getTitle() != null) {
            if (!StringUtils.hasText(request.getTitle())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title required");
            }
            String title = request.getTitle().trim();
            if (title.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title length must be <=100");
            }
            row.setTitle(title);
        }
        if (request.getCategory() != null) {
            String category = StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : null;
            if (category != null && category.length() > 50) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category length must be <=50");
            }
            row.setCategory(category);
        }
        if (request.getPrice() != null) {
            int price = request.getPrice().intValue();
            if (price < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price invalid");
            }
            row.setPrice(price);
        }
        if (request.getCoverUrl() != null) {
            row.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);
        }
        if (request.getVideoUrl() != null) {
            if (!StringUtils.hasText(request.getVideoUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoUrl required");
            }
            row.setVideoUrl(request.getVideoUrl().trim());
        }
        if (request.getDescription() != null) {
            row.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        }

        coachVideoMapper.updateById(row);
        return myDetail(principal, id);
    }

    @Transactional
    public CoachVideoDetailResponse updateStatus(AuthenticatedUser principal, Long id, CoachVideoStatusRequest request) {
        // 教练上下架：
        // - 仅允许 ON_SALE / OFF_SALE
        // - 安全边界：仅能操作自己的视频
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }

        String status = request.getStatus().trim();
        if (!Objects.equals(status, "ON_SALE") && !Objects.equals(status, "OFF_SALE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
        }

        CoachVideo row = coachVideoMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }
        if (!Objects.equals(row.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        row.setStatus(status);
        coachVideoMapper.updateById(row);
        return myDetail(principal, id);
    }

    public CoachVideoPageResponse myVideos(AuthenticatedUser principal, Integer page, Integer size, String status) {
        // 教练端：我的视频列表
        // - 手写分页（LIMIT/OFFSET），并对 page/size 做边界收敛（size 最大 100）
        // - status 可选：不传则返回全部
        Long coachUserId = requireUserId(principal);

        int p = page == null ? 1 : page.intValue();
        int s = size == null ? 20 : size.intValue();
        if (p < 1) {
            p = 1;
        }
        if (s < 1) {
            s = 1;
        }
        if (s > 100) {
            s = 100;
        }

        LambdaQueryWrapper<CoachVideo> countQw = new LambdaQueryWrapper<CoachVideo>()
            .eq(CoachVideo::getCoachUserId, coachUserId);
        if (StringUtils.hasText(status)) {
            countQw.eq(CoachVideo::getStatus, status);
        }
        long total = coachVideoMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<CoachVideoListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachVideo> listQw = new LambdaQueryWrapper<CoachVideo>()
                .eq(CoachVideo::getCoachUserId, coachUserId);
            if (StringUtils.hasText(status)) {
                listQw.eq(CoachVideo::getStatus, status);
            }
            listQw.orderByDesc(CoachVideo::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<CoachVideo> rows = coachVideoMapper.selectList(listQw);
            // 教练端列表不需要 purchased/purchaseCount 等“用户态/运营态”，因此直接复用 toListItems 并传 null
            items = toListItems(rows, null, null);
        }

        CoachVideoPageResponse resp = new CoachVideoPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public CoachVideoDetailResponse myDetail(AuthenticatedUser principal, Long id) {
        // 教练端：查看我的视频详情
        // - includeVideoUrl = true：教练本人查看时可以直接拿到 videoUrl
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachVideo row = coachVideoMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }
        if (!Objects.equals(row.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        CoachVideoDetailResponse resp = toDetail(row, null, true);
        SysUser coach = sysUserMapper.selectById(row.getCoachUserId());
        resp.setCoachUsername(coach == null ? null : coach.getUsername());
        resp.setPurchased(true);
        return resp;
    }

    @Transactional
    public String uploadAndUpdateCover(AuthenticatedUser principal, Long id, MultipartFile file) {
        // 上传并更新封面：
        // - 先校验视频存在且属于本人
        // - 上传服务（UploadService）负责把图片存储到统一介质并返回 URL
        // - 本表仅保存封面 URL
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }

        CoachVideo row = coachVideoMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }
        if (!Objects.equals(row.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String url = uploadService.uploadPhoto("coach", file).getUrl();
        row.setCoverUrl(url);
        coachVideoMapper.updateById(row);
        return url;
    }

    @Transactional
    public List<String> listDistinctPublicCategories() {
        // 用户端：公开分类列表
        // - 只统计 ON_SALE 且 category 非空的数据
        // - 使用 groupBy 做去重，并用 LinkedHashSet 保持输出稳定（便于前端缓存/比对）
        List<CoachVideo> rows = coachVideoMapper.selectList(
            new LambdaQueryWrapper<CoachVideo>()
                .select(CoachVideo::getCategory)
                .eq(CoachVideo::getStatus, "ON_SALE")
                .isNotNull(CoachVideo::getCategory)
                .ne(CoachVideo::getCategory, "")
                .groupBy(CoachVideo::getCategory)
                .orderByAsc(CoachVideo::getCategory)
        );

        Set<String> out = new LinkedHashSet<>();
        if (rows != null) {
            for (CoachVideo r : rows) {
                String c = r == null ? null : r.getCategory();
                if (StringUtils.hasText(c)) {
                    out.add(c.trim());
                }
            }
        }
        return new ArrayList<>(out);
    }

    public CoachVideoPageResponse listPublic(AuthenticatedUser principal, Integer page, Integer size, Long coachUserId, String category, String keyword) {
        // 用户端：公开目录
        // - 仅返回 ON_SALE
        // - 登录用户会额外计算 purchased（是否已购买/是否免费）
        // - keyword 做 title like 查询
        Long userId = principal == null ? null : principal.userId();

        int p = page == null ? 1 : page.intValue();
        int s = size == null ? 20 : size.intValue();
        if (p < 1) {
            p = 1;
        }
        if (s < 1) {
            s = 1;
        }
        if (s > 100) {
            s = 100;
        }

        LambdaQueryWrapper<CoachVideo> countQw = new LambdaQueryWrapper<CoachVideo>()
            .eq(CoachVideo::getStatus, "ON_SALE");
        if (coachUserId != null) {
            countQw.eq(CoachVideo::getCoachUserId, coachUserId);
        }
        if (StringUtils.hasText(category)) {
            countQw.eq(CoachVideo::getCategory, category.trim());
        }
        if (StringUtils.hasText(keyword)) {
            countQw.like(CoachVideo::getTitle, keyword.trim());
        }
        long total = coachVideoMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<CoachVideoListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachVideo> listQw = new LambdaQueryWrapper<CoachVideo>()
                .eq(CoachVideo::getStatus, "ON_SALE");
            if (coachUserId != null) {
                listQw.eq(CoachVideo::getCoachUserId, coachUserId);
            }
            if (StringUtils.hasText(category)) {
                listQw.eq(CoachVideo::getCategory, category.trim());
            }
            if (StringUtils.hasText(keyword)) {
                listQw.like(CoachVideo::getTitle, keyword.trim());
            }
            listQw.orderByDesc(CoachVideo::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<CoachVideo> rows = coachVideoMapper.selectList(listQw);
            // 购买态（purchasedIds）只对登录用户计算，避免匿名访问时的额外查询
            Set<Long> purchasedIds = userId == null ? new HashSet<>() : loadPurchasedVideoIds(userId, rows);
            items = toListItems(rows, purchasedIds, userId);
        }

        CoachVideoPageResponse resp = new CoachVideoPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public CoachVideoDetailResponse publicDetail(AuthenticatedUser principal, Long id) {
        // 用户端：公开视频详情
        // - 仅允许查看 ON_SALE
        // - purchased 决定是否下发 videoUrl：
        //   - 免费（price==0）视为已购买
        //   - 登录用户：查询购买表 status=PAID 判断是否已购买
        //   - 未购买时 includeVideoUrl=false，从而不返回真实播放地址
        Long userId = principal == null ? null : principal.userId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachVideo row = coachVideoMapper.selectById(id);
        if (row == null || !Objects.equals(row.getStatus(), "ON_SALE")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }

        boolean purchased = row.getPrice() != null && row.getPrice() == 0;
        if (!purchased && userId != null) {
            CoachVideoPurchase p = coachVideoPurchaseMapper.selectOne(new LambdaQueryWrapper<CoachVideoPurchase>()
                .eq(CoachVideoPurchase::getUserId, userId)
                .eq(CoachVideoPurchase::getVideoId, row.getId())
                .eq(CoachVideoPurchase::getStatus, "PAID"));
            purchased = p != null;
        }

        CoachVideoDetailResponse resp = toDetail(row, purchased, purchased);
        SysUser coach = sysUserMapper.selectById(row.getCoachUserId());
        resp.setCoachUsername(coach == null ? null : coach.getUsername());
        return resp;
    }

    @Transactional
    public VideoPurchaseResponse purchase(AuthenticatedUser principal, Long videoId) {
        // 用户购买/解锁视频：
        // - 事务边界内完成：
        //   1) 校验视频存在且 ON_SALE
        //   2) 幂等：若已存在购买记录则直接返回
        //   3) 需要付费则钱包扣款（debit）
        //   4) 写入购买记录（status=PAID）
        //   5) 给教练入账（credit）
        //
        // 并发与幂等：
        // - 通常依赖 (user_id, video_id) 唯一约束
        // - 并发插入触发 DuplicateKeyException 时再回查一次，作为补偿路径
        Long userId = requireUserId(principal);
        if (videoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoId required");
        }

        CoachVideo video = coachVideoMapper.selectById(videoId);
        if (video == null || !Objects.equals(video.getStatus(), "ON_SALE")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }

        CoachVideoPurchase existing = coachVideoPurchaseMapper.selectOne(new LambdaQueryWrapper<CoachVideoPurchase>()
            .eq(CoachVideoPurchase::getUserId, userId)
            .eq(CoachVideoPurchase::getVideoId, videoId));
        if (existing != null) {
            return toPurchaseResponse(existing);
        }

        int amount = video.getPrice() == null ? 0 : video.getPrice().intValue();
        if (amount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid amount");
        }

        if (amount > 0) {
            // 扣款：由 WalletService 保证余额校验与记账一致性
            walletService.debit(userId, amount, "COACH_VIDEO", "coach video", "COACH_VIDEO", videoId);
        }

        CoachVideoPurchase row = new CoachVideoPurchase();
        row.setPurchaseNo(UUID.randomUUID().toString().replace("-", ""));
        row.setUserId(userId);
        row.setVideoId(videoId);
        row.setAmount(amount);
        row.setStatus("PAID");
        row.setCreatedAt(LocalDateTime.now());
        try {
            coachVideoPurchaseMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发补偿：两次请求同时购买时，可能一个先插入成功
            CoachVideoPurchase again = coachVideoPurchaseMapper.selectOne(new LambdaQueryWrapper<CoachVideoPurchase>()
                .eq(CoachVideoPurchase::getUserId, userId)
                .eq(CoachVideoPurchase::getVideoId, videoId));
            if (again != null) {
                return toPurchaseResponse(again);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already purchased");
        }

        if (amount > 0 && video.getCoachUserId() != null) {
            // 入账：将用户支付金额计入教练钱包（收益）
            walletService.credit(video.getCoachUserId(), amount, "COACH_VIDEO_EARNING", "video earning", "COACH_VIDEO_PURCHASE", row.getId());
        }

        return toPurchaseResponse(row);
    }

    public VideoPurchasePageResponse myPurchases(AuthenticatedUser principal, Integer page, Integer size) {
        // 用户端：我的购买记录
        // - 购买记录表只存 videoId/amount/status 等，因此需要批量回查视频与教练信息以供展示
        // - 这里采用：purchaseRows -> videoIds 批量查 video -> coachIds 批量查 user，避免 N+1
        Long userId = requireUserId(principal);

        int p = page == null ? 1 : page.intValue();
        int s = size == null ? 20 : size.intValue();
        if (p < 1) {
            p = 1;
        }
        if (s < 1) {
            s = 1;
        }
        if (s > 100) {
            s = 100;
        }

        LambdaQueryWrapper<CoachVideoPurchase> countQw = new LambdaQueryWrapper<CoachVideoPurchase>()
            .eq(CoachVideoPurchase::getUserId, userId);
        long total = coachVideoPurchaseMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<VideoPurchaseListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachVideoPurchase> listQw = new LambdaQueryWrapper<CoachVideoPurchase>()
                .eq(CoachVideoPurchase::getUserId, userId)
                .orderByDesc(CoachVideoPurchase::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<CoachVideoPurchase> rows = coachVideoPurchaseMapper.selectList(listQw);

            Set<Long> videoIds = rows.stream().map(CoachVideoPurchase::getVideoId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, CoachVideo> videoMap = new HashMap<>();
            if (!videoIds.isEmpty()) {
                List<CoachVideo> videos = coachVideoMapper.selectBatchIds(videoIds);
                if (videos != null) {
                    for (CoachVideo v : videos) {
                        if (v != null && v.getId() != null) {
                            videoMap.put(v.getId(), v);
                        }
                    }
                }
            }

            Map<Long, SysUser> coachMap = new HashMap<>();
            if (!videoMap.isEmpty()) {
                Set<Long> coachIds = videoMap.values().stream()
                    .map(CoachVideo::getCoachUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                if (!coachIds.isEmpty()) {
                    List<SysUser> coaches = sysUserMapper.selectBatchIds(coachIds);
                    if (coaches != null) {
                        for (SysUser u : coaches) {
                            if (u != null && u.getId() != null) {
                                coachMap.put(u.getId(), u);
                            }
                        }
                    }
                }
            }

            for (CoachVideoPurchase r : rows) {
                // 将购买记录 + 视频信息 + 教练展示信息拼装成前端可直接渲染的列表项
                VideoPurchaseListItem it = new VideoPurchaseListItem();
                it.setId(r.getId());
                it.setPurchaseNo(r.getPurchaseNo());
                it.setVideoId(r.getVideoId());
                CoachVideo v = r.getVideoId() == null ? null : videoMap.get(r.getVideoId());
                it.setVideoTitle(v == null ? null : v.getTitle());
                it.setTitle(v == null ? null : v.getTitle());
                it.setCoverUrl(v == null ? null : v.getCoverUrl());
                Long coachUserId = v == null ? null : v.getCoachUserId();
                it.setCoachUserId(coachUserId);
                SysUser coach = coachUserId == null ? null : coachMap.get(coachUserId);
                if (coach != null) {
                    it.setCoachName(StringUtils.hasText(coach.getNickname()) ? coach.getNickname() : coach.getUsername());
                    it.setCoachAvatar(coach.getAvatarUrl());
                }
                it.setAmount(r.getAmount());
                it.setStatus(r.getStatus());
                it.setCreatedAt(r.getCreatedAt());
                it.setPurchasedAt(r.getCreatedAt());
                items.add(it);
            }
        }

        VideoPurchasePageResponse resp = new VideoPurchasePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private Set<Long> loadPurchasedVideoIds(Long userId, List<CoachVideo> videos) {
        // 批量加载“用户已购买的视频 id 集合”，用于目录列表上打 purchased 标识
        // - 只统计 status=PAID 的记录
        if (userId == null || videos == null || videos.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> ids = videos.stream().map(CoachVideo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashSet<>();
        }
        List<CoachVideoPurchase> rows = coachVideoPurchaseMapper.selectList(new LambdaQueryWrapper<CoachVideoPurchase>()
            .eq(CoachVideoPurchase::getUserId, userId)
            .in(CoachVideoPurchase::getVideoId, ids)
            .eq(CoachVideoPurchase::getStatus, "PAID"));
        Set<Long> purchasedIds = new HashSet<>();
        for (CoachVideoPurchase r : rows) {
            if (r != null && r.getVideoId() != null) {
                purchasedIds.add(r.getVideoId());
            }
        }
        return purchasedIds;
    }

    private List<CoachVideoListItem> toListItems(List<CoachVideo> rows, Set<Long> purchasedIds, Long userId) {
        // 将 CoachVideo 列表转换为“列表项 DTO”
        // - purchaseCount：购买次数（用于展示热度/销量）
        // - purchased：当前登录用户是否已购买（匿名时返回 null，避免前端误解为 false）
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> videoIds = rows.stream().map(CoachVideo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Long> purchaseCountMap = new HashMap<>();
        if (!videoIds.isEmpty()) {
            // 批量查询已支付购买记录，并在内存聚合出 purchaseCountMap（videoId -> count）
            List<CoachVideoPurchase> purchases = coachVideoPurchaseMapper.selectList(new LambdaQueryWrapper<CoachVideoPurchase>()
                .in(CoachVideoPurchase::getVideoId, videoIds)
                .eq(CoachVideoPurchase::getStatus, "PAID"));
            if (purchases != null) {
                for (CoachVideoPurchase p : purchases) {
                    if (p == null || p.getVideoId() == null) {
                        continue;
                    }
                    purchaseCountMap.put(p.getVideoId(), purchaseCountMap.getOrDefault(p.getVideoId(), 0L) + 1L);
                }
            }
        }

        Set<Long> coachIds = rows.stream().map(CoachVideo::getCoachUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> coachMap = new HashMap<>();
        if (!coachIds.isEmpty()) {
            // 批量查询教练用户信息，组装展示字段（coachUsername）
            List<SysUser> users = sysUserMapper.selectBatchIds(coachIds);
            if (users != null) {
                for (SysUser u : users) {
                    if (u != null && u.getId() != null) {
                        coachMap.put(u.getId(), u);
                    }
                }
            }
        }

        List<CoachVideoListItem> items = new ArrayList<>();
        for (CoachVideo row : rows) {
            CoachVideoListItem it = new CoachVideoListItem();
            it.setId(row.getId());
            it.setCoachUserId(row.getCoachUserId());
            SysUser cu = row.getCoachUserId() == null ? null : coachMap.get(row.getCoachUserId());
            it.setCoachUsername(cu == null ? null : cu.getUsername());
            it.setTitle(row.getTitle());
            it.setCategory(row.getCategory());
            it.setPrice(row.getPrice());
            it.setCoverUrl(row.getCoverUrl());
            it.setStatus(row.getStatus());
            boolean purchased = row.getPrice() != null && row.getPrice() == 0;
            if (!purchased && purchasedIds != null && row.getId() != null) {
                purchased = purchasedIds.contains(row.getId());
            }
            it.setPurchased(userId == null ? null : Boolean.valueOf(purchased));
            it.setPurchaseCount(row.getId() == null ? 0L : purchaseCountMap.getOrDefault(row.getId(), 0L));
            it.setCreatedAt(row.getCreatedAt());
            items.add(it);
        }
        return items;
    }

    private CoachVideoDetailResponse toDetail(CoachVideo row, Boolean purchased, boolean includeVideoUrl) {
        // 详情 DTO 组装：
        // - includeVideoUrl 是“是否下发真实播放地址”的核心控制点
        // - purchased 既用于前端展示，也用于决定 includeVideoUrl 的取值（见 publicDetail）
        CoachVideoDetailResponse resp = new CoachVideoDetailResponse();
        resp.setId(row.getId());
        resp.setCoachUserId(row.getCoachUserId());
        resp.setTitle(row.getTitle());
        resp.setCategory(row.getCategory());
        resp.setPrice(row.getPrice());
        resp.setCoverUrl(row.getCoverUrl());
        resp.setVideoUrl(includeVideoUrl ? row.getVideoUrl() : null);
        resp.setDescription(row.getDescription());
        resp.setStatus(row.getStatus());
        resp.setPurchased(purchased);
        long cnt = 0;
        if (row.getId() != null) {
            // 单视频的购买次数（已支付）
            cnt = coachVideoPurchaseMapper.selectCount(new LambdaQueryWrapper<CoachVideoPurchase>()
                .eq(CoachVideoPurchase::getVideoId, row.getId())
                .eq(CoachVideoPurchase::getStatus, "PAID"));
        }
        resp.setPurchaseCount(cnt);
        resp.setCreatedAt(row.getCreatedAt());
        return resp;
    }

    private VideoPurchaseResponse toPurchaseResponse(CoachVideoPurchase row) {
        // 购买响应 DTO：用于 purchase 接口返回（购买记录主键/单号/金额/状态/时间）
        VideoPurchaseResponse resp = new VideoPurchaseResponse();
        resp.setId(row.getId());
        resp.setPurchaseNo(row.getPurchaseNo());
        resp.setUserId(row.getUserId());
        resp.setVideoId(row.getVideoId());
        resp.setAmount(row.getAmount());
        resp.setStatus(row.getStatus());
        resp.setCreatedAt(row.getCreatedAt());
        return resp;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        // 统一的认证校验：缺少登录态时抛 401
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
