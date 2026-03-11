package com.communitysport.venue.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.upload.service.UploadService;
import com.communitysport.venue.dto.VenueCreateRequest;
import com.communitysport.venue.dto.VenueDetailResponse;
import com.communitysport.venue.dto.VenueListItem;
import com.communitysport.venue.dto.VenuePageResponse;
import com.communitysport.venue.dto.VenueTypeItem;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.entity.VenueType;
import com.communitysport.venue.mapper.VenueMapper;
import com.communitysport.venue.mapper.VenueTypeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 场地服务（Venue 业务核心）。
 *
 * <p>它覆盖的能力：
 * <p>- 场地类型：用于前端分类筛选
 * <p>- 场地列表/详情：用于用户端浏览
 * <p>- 后台维护：新增/编辑/上下架（状态）、上传图片
 *
 * <p>关于图片字段 cover_url：
 * <p>- 数据库字段是一个字符串，但实际存储的是“图片 URL 列表”的 JSON（例如 ["/upload/a.jpg","/upload/b.jpg"]）
 * <p>- 这样做的好处：不需要单独建场地图片表，快速实现多图
 * <p>- 代价：查询/更新需要序列化/反序列化，且字段长度受限（本项目做了 255 长度保护）
 */
@Service
public class VenueService {

    private final VenueTypeMapper venueTypeMapper;

    private final VenueMapper venueMapper;

    private final UploadService uploadService;

    private final ObjectMapper objectMapper;

    public VenueService(VenueTypeMapper venueTypeMapper, VenueMapper venueMapper, UploadService uploadService, ObjectMapper objectMapper) {
        this.venueTypeMapper = venueTypeMapper;
        this.venueMapper = venueMapper;
        this.uploadService = uploadService;
        this.objectMapper = objectMapper;
    }

    public List<VenueTypeItem> listTypes() {
        // 场地类型一般是“字典表”性质的数据：变动少、读多。
        List<VenueType> rows = venueTypeMapper.selectList(new LambdaQueryWrapper<VenueType>().orderByAsc(VenueType::getId));
        List<VenueTypeItem> items = new ArrayList<>();
        for (VenueType row : rows) {
            VenueTypeItem item = new VenueTypeItem();
            item.setId(row.getId());
            item.setName(row.getName());
            items.add(item);
        }
        return items;
    }

    public VenuePageResponse listVenues(Integer page, Integer size, Long typeId, String keyword, String status) {
        // 场地列表：分页 + 条件筛选。
        //
        // 这里的实现方式：
        // 1）先 count 总数
        // 2）再按 LIMIT/OFFSET 查当前页
        // 3）补充 typeName（避免前端再查类型表）
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

        LambdaQueryWrapper<Venue> countQw = buildVenueQuery(typeId, keyword, status);
        long total = venueMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<VenueListItem> items = new ArrayList<>();

        if (offset < total) {
            LambdaQueryWrapper<Venue> listQw = buildVenueQuery(typeId, keyword, status)
                .orderByDesc(Venue::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<Venue> rows = venueMapper.selectList(listQw);

            Map<Long, String> typeNameMap = loadTypeNames(rows);

            for (Venue row : rows) {
                VenueListItem item = new VenueListItem();
                item.setId(row.getId());
                item.setTypeId(row.getTypeId());
                item.setTypeName(typeNameMap.get(row.getTypeId()));
                item.setName(row.getName());
                item.setArea(row.getArea());
                item.setAddress(row.getAddress());
                item.setPricePerHour(row.getPricePerHour());
                List<String> coverUrls = parseCoverUrls(row.getCoverUrl());
                item.setCoverUrls(coverUrls);
                // coverUrl：为了兼容老字段/兜底显示，提供一个“单封面”字段。
                item.setCoverUrl(pickFirstCoverUrl(row.getCoverUrl(), coverUrls));
                item.setStatus(row.getStatus());
                item.setClickCount(row.getClickCount());
                items.add(item);
            }
        }

        VenuePageResponse resp = new VenuePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public VenueDetailResponse getVenue(Long id) {
        // 场地详情（纯查询，不修改 clickCount）。
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        Venue row = venueMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }

        VenueType type = row.getTypeId() == null ? null : venueTypeMapper.selectById(row.getTypeId());

        VenueDetailResponse resp = new VenueDetailResponse();
        resp.setId(row.getId());
        resp.setTypeId(row.getTypeId());
        resp.setTypeName(type == null ? null : type.getName());
        resp.setName(row.getName());
        resp.setArea(row.getArea());
        resp.setAddress(row.getAddress());
        resp.setSpec(row.getSpec());
        resp.setOpenTimeDesc(row.getOpenTimeDesc());
        resp.setPricePerHour(row.getPricePerHour());
        resp.setContactPhone(row.getContactPhone());
        List<String> coverUrls = parseCoverUrls(row.getCoverUrl());
        resp.setCoverUrls(coverUrls);
        resp.setCoverUrl(pickFirstCoverUrl(row.getCoverUrl(), coverUrls));
        resp.setDescription(row.getDescription());
        resp.setStatus(row.getStatus());
        resp.setClickCount(row.getClickCount());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    @Transactional
    public VenueDetailResponse publicGetVenueAndIncreaseClick(Long id) {
        // 公开访问详情：增加 clickCount。
        //
        // 这里的 clickCount 只是一个简单的“浏览量计数”，不是严格的 UV。
        // 更新失败也不影响详情展示，因此 catch 后忽略。
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        try {
            venueMapper.increaseClickCount(id);
        } catch (Exception ignored) {
        }
        return getVenue(id);
    }

    @Transactional
    public VenueDetailResponse createVenue(VenueCreateRequest request) {
        // 后台新增场地。
        if (request == null || request.getTypeId() == null || !StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "typeId/name required");
        }

        VenueType type = venueTypeMapper.selectById(request.getTypeId());
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid typeId");
        }

        Venue row = new Venue();
        row.setTypeId(request.getTypeId());
        row.setName(request.getName());
        row.setArea(request.getArea());
        row.setAddress(request.getAddress());
        row.setSpec(request.getSpec());
        row.setOpenTimeDesc(request.getOpenTimeDesc());
        row.setPricePerHour(request.getPricePerHour() == null ? 0 : request.getPricePerHour());
        row.setContactPhone(request.getContactPhone());
        row.setCoverUrl(request.getCoverUrl());
        row.setDescription(request.getDescription());

        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ACTIVE";
        // status 必须在允许集合中。
        validateVenueStatus(status);
        row.setStatus(status);
        row.setClickCount(0);

        venueMapper.insert(row);

        return getVenue(row.getId());
    }

    @Transactional
    public VenueDetailResponse updateVenue(Long id, VenueCreateRequest request) {
        // 后台编辑场地（全量更新主要字段）。
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || request.getTypeId() == null || !StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "typeId/name required");
        }

        Venue row = venueMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }

        VenueType type = venueTypeMapper.selectById(request.getTypeId());
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid typeId");
        }

        row.setTypeId(request.getTypeId());
        row.setName(request.getName());
        row.setArea(request.getArea());
        row.setAddress(request.getAddress());
        row.setSpec(request.getSpec());
        row.setOpenTimeDesc(request.getOpenTimeDesc());
        row.setPricePerHour(request.getPricePerHour() == null ? 0 : request.getPricePerHour());
        row.setContactPhone(request.getContactPhone());
        row.setDescription(request.getDescription());

        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : row.getStatus();
        validateVenueStatus(status);
        row.setStatus(status);

        venueMapper.updateById(row);
        return getVenue(id);
    }

    @Transactional
    public VenueDetailResponse updateStatus(Long id, String status) {
        // 单独更新状态：用于上架/下架/维护中。
        if (id == null || !StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id/status required");
        }
        validateVenueStatus(status);

        Venue row = venueMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }

        row.setStatus(status);
        venueMapper.updateById(row);
        return getVenue(id);
    }

    @Transactional
    public List<String> uploadVenuePhotos(Long id, MultipartFile[] files) {
        // 上传场地图片：
        // 1）调用 UploadService 保存文件并返回 URL
        // 2）把 URL 列表序列化为 JSON，写回 venue.cover_url
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "files required");
        }

        Venue row = venueMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }

        List<String> urls = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            urls.add(uploadService.uploadPhoto("venue", f).getUrl());
        }
        if (urls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "files required");
        }

        String json;
        try {
            // 这里把 List<String> 转成 JSON 数组字符串。
            json = objectMapper.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize urls");
        }

        if (json.length() > 255) {
            // 如果你希望一个场地支持更多图片，建议：
            // 1）把 venue.cover_url 扩容到 TEXT
            // 2）或建立 venue_photo 子表（一行一张图）
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cover_url too long, please enlarge venue.cover_url column");
        }

        row.setCoverUrl(json);
        venueMapper.updateById(row);
        return urls;
    }

    private List<String> parseCoverUrls(String coverUrl) {
        // 解析 cover_url：
        // - 如果不是 JSON 数组（不以 [ 开头），直接返回空列表
        // - 解析失败也返回空列表（兼容历史脏数据）
        if (!StringUtils.hasText(coverUrl)) {
            return List.of();
        }
        String s = coverUrl.trim();
        if (!s.startsWith("[")) {
            return List.of();
        }
        try {
            List<String> urls = objectMapper.readValue(s, new TypeReference<List<String>>() {});
            return urls == null ? List.of() : urls;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String pickFirstCoverUrl(String raw, List<String> parsed) {
        if (parsed != null && !parsed.isEmpty() && StringUtils.hasText(parsed.get(0))) {
            return parsed.get(0);
        }
        return raw;
    }

    private LambdaQueryWrapper<Venue> buildVenueQuery(Long typeId, String keyword, String status) {
        // 构造查询条件：把“可选条件”拼装到 MyBatis-Plus 的 LambdaQueryWrapper。
        LambdaQueryWrapper<Venue> qw = new LambdaQueryWrapper<>();
        if (typeId != null) {
            qw.eq(Venue::getTypeId, typeId);
        }
        if (StringUtils.hasText(status)) {
            qw.eq(Venue::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Venue::getName, keyword)
                .or().like(Venue::getArea, keyword)
                .or().like(Venue::getAddress, keyword));
        }
        return qw;
    }

    private void validateVenueStatus(String status) {
        // 状态枚举（字符串）：
        // - ACTIVE：可预约
        // - MAINTENANCE：维护中（一般不开放预约）
        // - DISABLED：下架
        if (!Objects.equals(status, "ACTIVE") && !Objects.equals(status, "MAINTENANCE") && !Objects.equals(status, "DISABLED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }
    }

    private Map<Long, String> loadTypeNames(List<Venue> rows) {
        Set<Long> ids = rows.stream().map(Venue::getTypeId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<VenueType> types = venueTypeMapper.selectByIds(ids);
        Map<Long, String> map = new HashMap<>();
        for (VenueType t : types) {
            map.put(t.getId(), t.getName());
        }
        return map;
    }
}
