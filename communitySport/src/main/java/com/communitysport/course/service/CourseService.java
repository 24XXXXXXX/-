package com.communitysport.course.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.course.dto.CourseCreateRequest;
import com.communitysport.course.dto.CourseDetailResponse;
import com.communitysport.course.dto.CourseListItem;
import com.communitysport.course.dto.CoursePageResponse;
import com.communitysport.course.dto.CourseStatusRequest;
import com.communitysport.course.dto.CourseUpdateRequest;
import com.communitysport.course.entity.CoachCourse;
import com.communitysport.course.mapper.CoachCourseMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.mapper.VenueMapper;

@Service
public class CourseService {

    private final CoachCourseMapper coachCourseMapper;

    private final SysUserMapper sysUserMapper;

    private final VenueMapper venueMapper;

    public CourseService(CoachCourseMapper coachCourseMapper, SysUserMapper sysUserMapper, VenueMapper venueMapper) {
        this.coachCourseMapper = coachCourseMapper;
        this.sysUserMapper = sysUserMapper;
        this.venueMapper = venueMapper;
    }

    @Transactional
    public List<String> listDistinctCategories() {
        // 教练端使用的“分类枚举”接口（不限定上架状态）。
        //
        // 设计动机：
        // - 分类是自由文本（不是单独字典表），因此需要从历史数据中“反向聚合”出可选项
        // - 便于教练编辑时复用已有分类，减少随意输入导致的分类碎片化
        List<CoachCourse> rows = coachCourseMapper.selectList(
            new LambdaQueryWrapper<CoachCourse>()
                .select(CoachCourse::getCategory)
                .isNotNull(CoachCourse::getCategory)
                .ne(CoachCourse::getCategory, "")
                .groupBy(CoachCourse::getCategory)
                .orderByAsc(CoachCourse::getCategory)
        );

        Set<String> out = new LinkedHashSet<>();
        if (rows != null) {
            for (CoachCourse r : rows) {
                String c = r == null ? null : r.getCategory();
                if (StringUtils.hasText(c)) {
                    out.add(c.trim());
                }
            }
        }
        return new ArrayList<>(out);
    }

    @Transactional
    public List<String> listDistinctPublicCategories() {
        // 公开侧使用的“分类枚举”接口：只统计 ON_SALE 的课程。
        //
        // 这样前端下拉框不会出现“已经下架/不可购买”的分类项，减少用户困惑。
        List<CoachCourse> rows = coachCourseMapper.selectList(
            new LambdaQueryWrapper<CoachCourse>()
                .select(CoachCourse::getCategory)
                .eq(CoachCourse::getStatus, "ON_SALE")
                .isNotNull(CoachCourse::getCategory)
                .ne(CoachCourse::getCategory, "")
                .groupBy(CoachCourse::getCategory)
                .orderByAsc(CoachCourse::getCategory)
        );

        Set<String> out = new LinkedHashSet<>();
        if (rows != null) {
            for (CoachCourse r : rows) {
                String c = r == null ? null : r.getCategory();
                if (StringUtils.hasText(c)) {
                    out.add(c.trim());
                }
            }
        }
        return new ArrayList<>(out);
    }

    @Transactional
    public CoursePageResponse listPublic(Integer page, Integer size, String keyword, String category, Long coachUserId, Long venueId) {
        // 公开课程列表：只返回 ON_SALE。
        //
        // 这里的实现特点：
        // - 先 count 再分页查询（传统分页方式，便于前端展示 total）
        // - DTO（CourseListItem）做了“反范式化”：把 coachUsername、venueName 也一并返回
        //   避免前端为了显示一个列表再发 N 次请求查询教练/场馆名称（典型 N+1 问题）
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

        LambdaQueryWrapper<CoachCourse> countQw = buildPublicQuery(keyword, category, coachUserId, venueId);
        long total = coachCourseMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<CourseListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachCourse> listQw = buildPublicQuery(keyword, category, coachUserId, venueId)
                .orderByDesc(CoachCourse::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<CoachCourse> rows = coachCourseMapper.selectList(listQw);

            Map<Long, String> coachNameMap = loadCoachNames(rows);
            Map<Long, String> venueNameMap = loadVenueNames(rows);

            for (CoachCourse row : rows) {
                items.add(toListItem(row, coachNameMap.get(row.getCoachUserId()), venueNameMap.get(row.getVenueId())));
            }
        }

        CoursePageResponse resp = new CoursePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public CourseDetailResponse getPublic(Long id) {
        // 公开课程详情：只允许访问 ON_SALE。
        //
        // 为什么下架课程返回 404 而不是 403？
        // - 对“公开接口”而言，下架课程等同于不存在
        // - 可以减少信息泄露（例如课程是否存在、是否下架）
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachCourse row = coachCourseMapper.selectById(id);
        if (row == null || !Objects.equals(row.getStatus(), "ON_SALE")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        String coachUsername = loadCoachName(row.getCoachUserId());
        String venueName = row.getVenueId() == null ? null : loadVenueName(row.getVenueId());
        return toDetail(row, coachUsername, venueName);
    }

    @Transactional
    public CourseDetailResponse create(AuthenticatedUser principal, CourseCreateRequest request) {
        // 教练创建课程。
        //
        // 关键点：
        // - price 由教练设置，但必须 >= 0；金额单位与钱包一致（通常是“分”）
        // - capacity 是课程的“默认容量”，课次可覆盖（见 CourseSessionService.create）
        // - venueId 是可选关联：课程可以绑定某个场馆（用于展示上课地点/做筛选）
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (request == null || !StringUtils.hasText(request.getTitle()) || request.getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title/price required");
        }

        String title = request.getTitle().trim();
        if (title.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title too long");
        }

        int price = request.getPrice().intValue();
        if (price < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be >=0");
        }

        int capacity = request.getCapacity() == null ? 1 : request.getCapacity().intValue();
        if (capacity < 1 || capacity > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity must be 1-1000");
        }

        if (request.getVenueId() != null) {
            Venue v = venueMapper.selectById(request.getVenueId());
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venue not found");
            }
        }

        CoachCourse row = new CoachCourse();
        row.setCoachUserId(principal.userId());
        row.setTitle(title);
        if (StringUtils.hasText(request.getCategory())) {
            row.setCategory(request.getCategory().trim());
        }
        row.setDurationMinutes(request.getDurationMinutes());
        row.setPrice(price);
        row.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);
        row.setVenueId(request.getVenueId());
        row.setCapacity(capacity);
        if (StringUtils.hasText(request.getOutline())) {
            row.setOutline(request.getOutline().trim());
        }
        row.setStatus("ON_SALE");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());

        coachCourseMapper.insert(row);

        CoachCourse after = row.getId() == null ? null : coachCourseMapper.selectById(row.getId());
        String coachUsername = principal.username();
        String venueName = after == null || after.getVenueId() == null ? null : loadVenueName(after.getVenueId());
        return toDetail(after, coachUsername, venueName);
    }

    @Transactional
    public CourseDetailResponse update(AuthenticatedUser principal, Long id, CourseUpdateRequest request) {
        // 教练编辑课程。
        //
        // 安全性：
        // - 强制校验 existing.coach_user_id == 当前用户
        // - 避免越权修改他人课程
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        CoachCourse existing = coachCourseMapper.selectById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        if (!Objects.equals(existing.getCoachUserId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (StringUtils.hasText(request.getTitle())) {
            String title = request.getTitle().trim();
            if (title.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title too long");
            }
            existing.setTitle(title);
        }
        if (request.getCategory() != null) {
            existing.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : null);
        }
        if (request.getDurationMinutes() != null) {
            existing.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getPrice() != null) {
            int price = request.getPrice().intValue();
            if (price < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be >=0");
            }
            existing.setPrice(price);
        }
        if (request.getCoverUrl() != null) {
            existing.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);
        }
        if (request.getVenueId() != null) {
            Venue v = venueMapper.selectById(request.getVenueId());
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venue not found");
            }
            existing.setVenueId(request.getVenueId());
        }
        if (request.getCapacity() != null) {
            int capacity = request.getCapacity().intValue();
            if (capacity < 1 || capacity > 1000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity must be 1-1000");
            }
            existing.setCapacity(capacity);
        }
        if (request.getOutline() != null) {
            existing.setOutline(StringUtils.hasText(request.getOutline()) ? request.getOutline().trim() : null);
        }

        existing.setUpdatedAt(LocalDateTime.now());
        coachCourseMapper.updateById(existing);

        CoachCourse after = coachCourseMapper.selectById(id);
        String venueName = after == null || after.getVenueId() == null ? null : loadVenueName(after.getVenueId());
        return toDetail(after, principal.username(), venueName);
    }

    @Transactional
    public CourseDetailResponse updateStatus(AuthenticatedUser principal, Long id, CourseStatusRequest request) {
        // 教练上下架课程。
        //
        // 状态意义：
        // - ON_SALE：公开可见，可被预约
        // - OFF_SALE：公开不可见（但不会删除数据，便于再次上架/追溯历史）
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }
        String status = request.getStatus().trim();
        if (!Objects.equals(status, "ON_SALE") && !Objects.equals(status, "OFF_SALE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ON_SALE/OFF_SALE");
        }

        CoachCourse existing = coachCourseMapper.selectById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        if (!Objects.equals(existing.getCoachUserId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        existing.setStatus(status);
        existing.setUpdatedAt(LocalDateTime.now());
        coachCourseMapper.updateById(existing);

        CoachCourse after = coachCourseMapper.selectById(id);
        String venueName = after == null || after.getVenueId() == null ? null : loadVenueName(after.getVenueId());
        return toDetail(after, principal.username(), venueName);
    }

    @Transactional
    public CoursePageResponse myCourses(AuthenticatedUser principal, Integer page, Integer size, String status) {
        // 教练端：我的课程列表。
        //
        // 与 listPublic 的区别：
        // - 只按 coach_user_id 过滤，不强制 ON_SALE
        // - coachUsername 直接使用 principal.username（无需再查库）
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

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

        LambdaQueryWrapper<CoachCourse> countQw = new LambdaQueryWrapper<CoachCourse>().eq(CoachCourse::getCoachUserId, principal.userId());
        if (StringUtils.hasText(status)) {
            countQw.eq(CoachCourse::getStatus, status);
        }
        long total = coachCourseMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<CourseListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachCourse> listQw = new LambdaQueryWrapper<CoachCourse>().eq(CoachCourse::getCoachUserId, principal.userId());
            if (StringUtils.hasText(status)) {
                listQw.eq(CoachCourse::getStatus, status);
            }
            listQw.orderByDesc(CoachCourse::getId).last("LIMIT " + s + " OFFSET " + offset);

            List<CoachCourse> rows = coachCourseMapper.selectList(listQw);
            Map<Long, String> venueNameMap = loadVenueNames(rows);
            for (CoachCourse row : rows) {
                items.add(toListItem(row, principal.username(), venueNameMap.get(row.getVenueId())));
            }
        }

        CoursePageResponse resp = new CoursePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public CourseDetailResponse myCourseDetail(AuthenticatedUser principal, Long id) {
        // 教练端：我的课程详情。
        //
        // 与 getPublic 的区别：
        // - 不要求 ON_SALE，只要课程属于当前教练即可查看
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachCourse row = coachCourseMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        if (!Objects.equals(row.getCoachUserId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String venueName = row.getVenueId() == null ? null : loadVenueName(row.getVenueId());
        return toDetail(row, principal.username(), venueName);
    }

    private LambdaQueryWrapper<CoachCourse> buildPublicQuery(String keyword, String category, Long coachUserId, Long venueId) {
        // 公开列表的查询构造器（统一复用在 count 与 list 两处）。
        //
        // 注意：
        // - 这里固定加了 status=ON_SALE，是“公开侧数据隔离”的核心
        LambdaQueryWrapper<CoachCourse> qw = new LambdaQueryWrapper<CoachCourse>().eq(CoachCourse::getStatus, "ON_SALE");
        if (StringUtils.hasText(keyword)) {
            qw.like(CoachCourse::getTitle, keyword);
        }
        if (StringUtils.hasText(category)) {
            qw.eq(CoachCourse::getCategory, category);
        }
        if (coachUserId != null) {
            qw.eq(CoachCourse::getCoachUserId, coachUserId);
        }
        if (venueId != null) {
            qw.eq(CoachCourse::getVenueId, venueId);
        }
        return qw;
    }

    private CourseListItem toListItem(CoachCourse row, String coachUsername, String venueName) {
        if (row == null) {
            return null;
        }
        CourseListItem item = new CourseListItem();
        item.setId(row.getId());
        item.setCoachUserId(row.getCoachUserId());
        item.setCoachUsername(coachUsername);
        item.setTitle(row.getTitle());
        item.setCategory(row.getCategory());
        item.setDurationMinutes(row.getDurationMinutes());
        item.setPrice(row.getPrice());
        item.setCoverUrl(row.getCoverUrl());
        item.setVenueId(row.getVenueId());
        item.setVenueName(venueName);
        item.setCapacity(row.getCapacity());
        item.setStatus(row.getStatus());
        return item;
    }

    private CourseDetailResponse toDetail(CoachCourse row, String coachUsername, String venueName) {
        if (row == null) {
            return null;
        }
        CourseDetailResponse resp = new CourseDetailResponse();
        resp.setId(row.getId());
        resp.setCoachUserId(row.getCoachUserId());
        resp.setCoachUsername(coachUsername);
        resp.setTitle(row.getTitle());
        resp.setCategory(row.getCategory());
        resp.setDurationMinutes(row.getDurationMinutes());
        resp.setPrice(row.getPrice());
        resp.setCoverUrl(row.getCoverUrl());
        resp.setVenueId(row.getVenueId());
        resp.setVenueName(venueName);
        resp.setCapacity(row.getCapacity());
        resp.setOutline(row.getOutline());
        resp.setStatus(row.getStatus());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    private Map<Long, String> loadCoachNames(List<CoachCourse> rows) {
        if (rows == null || rows.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> ids = rows.stream().map(CoachCourse::getCoachUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>();
        if (users != null) {
            for (SysUser u : users) {
                if (u != null && u.getId() != null) {
                    map.put(u.getId(), u.getUsername());
                }
            }
        }
        return map;
    }

    private String loadCoachName(Long coachUserId) {
        if (coachUserId == null) {
            return null;
        }
        SysUser u = sysUserMapper.selectById(coachUserId);
        return u == null ? null : u.getUsername();
    }

    private Map<Long, String> loadVenueNames(List<CoachCourse> rows) {
        if (rows == null || rows.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> ids = rows.stream().map(CoachCourse::getVenueId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<Venue> venues = venueMapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>();
        if (venues != null) {
            for (Venue v : venues) {
                if (v != null && v.getId() != null) {
                    map.put(v.getId(), v.getName());
                }
            }
        }
        return map;
    }

    private String loadVenueName(Long venueId) {
        if (venueId == null) {
            return null;
        }
        Venue v = venueMapper.selectById(venueId);
        return v == null ? null : v.getName();
    }
}
