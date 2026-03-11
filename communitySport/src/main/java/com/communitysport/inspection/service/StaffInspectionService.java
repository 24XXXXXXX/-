package com.communitysport.inspection.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.booking.entity.VenueVerificationLog;
import com.communitysport.booking.mapper.VenueVerificationLogMapper;
import com.communitysport.complaint.entity.Complaint;
import com.communitysport.complaint.mapper.ComplaintMapper;
import com.communitysport.equipment.entity.Equipment;
import com.communitysport.equipment.mapper.EquipmentMapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.inspection.dto.StaffDailyReportResponse;
import com.communitysport.inspection.dto.StaffInspectionCreateRequest;
import com.communitysport.inspection.dto.StaffInspectionDetailResponse;
import com.communitysport.inspection.dto.StaffInspectionListItem;
import com.communitysport.inspection.dto.StaffInspectionPageResponse;
import com.communitysport.inspection.entity.StaffInspectionReport;
import com.communitysport.inspection.mapper.StaffInspectionReportMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.staff.entity.StaffProfile;
import com.communitysport.staff.mapper.StaffProfileMapper;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.mapper.VenueMapper;
import com.communitysport.venue.service.VenueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StaffInspectionService {

    // 员工巡检（上报）模块核心服务：
    // - 员工端：创建上报、查看我的上报（分页/详情）、更新状态、查看个人日报
    // - 管理端：全量上报查询（分页/筛选）、查看详情、强制更新状态
    //
    // 关键设计点：
    // - targetType/issueType/status 均采用白名单，避免随意字符串污染数据口径
    // - attachments 以 JSON 字符串存库，接口层以 List<String> 暴露
    // - 员工端对“详情/更新状态”做资源级校验：只能操作本人上报
    // - 日报接口属于跨模块聚合：会统计场地、核销、投诉、巡检、器材库存等指标

    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of("VENUE", "EQUIPMENT", "OTHER");

    // 允许的目标类型：
    // - VENUE：关联 venueId
    // - EQUIPMENT：关联 equipmentId
    // - OTHER：不强制关联目标 id

    private static final Set<String> ALLOWED_ISSUE_TYPES = Set.of("MAINTENANCE", "REPAIR", "SHORTAGE", "DAMAGE", "OTHER");

    // 允许的问题类型：用于统计口径与后续流程分派（维修/补货等）

    private static final Set<String> ALLOWED_STATUSES = Set.of("SUBMITTED", "IN_PROGRESS", "RESOLVED");

    // 允许的状态机：SUBMITTED -> IN_PROGRESS -> RESOLVED

    private static final int LOW_STOCK_THRESHOLD = 10;

    // 低库存阈值：用于日报统计 equipmentLowStock

    private final StaffInspectionReportMapper staffInspectionReportMapper;

    private final StaffProfileMapper staffProfileMapper;

    private final VenueService venueService;

    private final VenueMapper venueMapper;

    private final EquipmentMapper equipmentMapper;

    private final VenueVerificationLogMapper venueVerificationLogMapper;

    private final ComplaintMapper complaintMapper;

    private final SysUserMapper sysUserMapper;

    private final ObjectMapper objectMapper;

    public StaffInspectionService(
            StaffInspectionReportMapper staffInspectionReportMapper,
            StaffProfileMapper staffProfileMapper,
            VenueService venueService,
            VenueMapper venueMapper,
            EquipmentMapper equipmentMapper,
            VenueVerificationLogMapper venueVerificationLogMapper,
            ComplaintMapper complaintMapper,
            SysUserMapper sysUserMapper,
            ObjectMapper objectMapper
    ) {
        this.staffInspectionReportMapper = staffInspectionReportMapper;
        this.staffProfileMapper = staffProfileMapper;
        this.venueService = venueService;
        this.venueMapper = venueMapper;
        this.equipmentMapper = equipmentMapper;
        this.venueVerificationLogMapper = venueVerificationLogMapper;
        this.complaintMapper = complaintMapper;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StaffInspectionDetailResponse create(AuthenticatedUser principal, StaffInspectionCreateRequest request) {
        // 员工创建巡检上报：
        // - region 从 StaffProfile 读取，用于管理端按片区筛选
        // - targetType=VENUE/EQUIPMENT 时要求对应 id 非空
        // - attachments 序列化为 JSON 字符串
        // - 初始状态 SUBMITTED
        Long staffUserId = requireUserId(principal);
        StaffProfile profile = requireStaffProfile(staffUserId);

        if (request == null || !StringUtils.hasText(request.getTargetType()) || !StringUtils.hasText(request.getIssueType())
                || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetType/issueType/content required");
        }

        String targetType = request.getTargetType().trim().toUpperCase();
        if (!ALLOWED_TARGET_TYPES.contains(targetType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetType invalid");
        }

        String issueType = request.getIssueType().trim().toUpperCase();
        if (!ALLOWED_ISSUE_TYPES.contains(issueType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issueType invalid");
        }

        Long venueId = request.getVenueId();
        Long equipmentId = request.getEquipmentId();
        if (Objects.equals(targetType, "VENUE") && venueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId required");
        }
        if (Objects.equals(targetType, "EQUIPMENT") && equipmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "equipmentId required");
        }

        String content = request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }
        if (content.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content too long");
        }

        String attachments = toJsonOrNull(request.getAttachments());

        // attachments：附件 URL 列表序列化

        LocalDateTime now = LocalDateTime.now();

        StaffInspectionReport row = new StaffInspectionReport();
        row.setStaffUserId(staffUserId);
        row.setRegion(StringUtils.hasText(profile.getRegion()) ? profile.getRegion().trim() : null);
        row.setTargetType(targetType);
        row.setVenueId(venueId);
        row.setEquipmentId(equipmentId);
        row.setIssueType(issueType);
        row.setContent(content);
        row.setAttachments(attachments);
        row.setStatus("SUBMITTED");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setResolvedAt(null);

        staffInspectionReportMapper.insert(row);

        if (Objects.equals(targetType, "VENUE") && StringUtils.hasText(request.getNewVenueStatus())) {
            // 可选联动：巡检上报时若指定 newVenueStatus，则直接更新场地状态
            // - 典型场景：发现维护/禁用原因后立即把场地状态调整为 MAINTENANCE/DISABLED
            venueService.updateStatus(venueId, request.getNewVenueStatus().trim());
        }

        return detail(principal, row.getId());
    }

    public StaffInspectionPageResponse myReports(
            AuthenticatedUser principal,
            Integer page,
            Integer size,
            String status,
            String targetType
    ) {
        Long staffUserId = requireUserId(principal);

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

        LambdaQueryWrapper<StaffInspectionReport> countQw = buildQuery(staffUserId, status, targetType);
        long total = staffInspectionReportMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<StaffInspectionListItem> items = new ArrayList<>();
        if (offset < total) {
            List<StaffInspectionReport> rows = staffInspectionReportMapper.selectList(
                buildQuery(staffUserId, status, targetType).orderByDesc(StaffInspectionReport::getId).last("LIMIT " + s + " OFFSET " + offset)
            );
            if (rows != null) {
                for (StaffInspectionReport r : rows) {
                    items.add(toListItem(r));
                }
            }
        }

        StaffInspectionPageResponse resp = new StaffInspectionPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public StaffInspectionDetailResponse detail(AuthenticatedUser principal, Long id) {
        // 员工端详情：只能查看本人上报（资源级别校验）
        Long staffUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        StaffInspectionReport row = staffInspectionReportMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(row.getStaffUserId(), staffUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return toDetail(row);
    }

    public StaffInspectionPageResponse adminList(Integer page, Integer size, String status, String targetType, String region) {
        // 管理端全量列表：按状态/目标类型/片区筛选
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

        LambdaQueryWrapper<StaffInspectionReport> qw = new LambdaQueryWrapper<StaffInspectionReport>();
        if (StringUtils.hasText(status)) {
            qw.eq(StaffInspectionReport::getStatus, status.trim().toUpperCase());
        }
        if (StringUtils.hasText(targetType)) {
            qw.eq(StaffInspectionReport::getTargetType, targetType.trim().toUpperCase());
        }
        if (StringUtils.hasText(region)) {
            qw.eq(StaffInspectionReport::getRegion, region.trim());
        }

        long total = staffInspectionReportMapper.selectCount(qw);
        long offset = (long) (p - 1) * s;
        List<StaffInspectionListItem> items = new ArrayList<>();
        if (offset < total) {
            List<StaffInspectionReport> rows = staffInspectionReportMapper.selectList(
                qw.orderByDesc(StaffInspectionReport::getId).last("LIMIT " + s + " OFFSET " + offset)
            );
            if (rows != null && !rows.isEmpty()) {
                Map<Long, String> usernameMap = loadUsernameMap(rows);
                for (StaffInspectionReport r : rows) {
                    items.add(toListItem(r, usernameMap.get(r == null ? null : r.getStaffUserId())));
                }
            }
        }

        StaffInspectionPageResponse resp = new StaffInspectionPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public StaffInspectionDetailResponse adminDetail(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        StaffInspectionReport row = staffInspectionReportMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        return toDetail(row, loadUsername(row.getStaffUserId()));
    }

    @Transactional
    public StaffInspectionDetailResponse adminUpdateStatus(Long id, String status) {
        // 管理端强制更新状态：
        // - 允许 SUBMITTED/IN_PROGRESS/RESOLVED
        // - 当回退到非 RESOLVED 时清空 resolvedAt，保持统计口径一致
        if (id == null || !StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id/status required");
        }

        String st = status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(st)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }

        StaffInspectionReport row = staffInspectionReportMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        row.setStatus(st);
        row.setUpdatedAt(LocalDateTime.now());
        if (Objects.equals(st, "RESOLVED")) {
            row.setResolvedAt(LocalDateTime.now());
        } else {
            row.setResolvedAt(null);
        }
        staffInspectionReportMapper.updateById(row);
        return toDetail(row, loadUsername(row.getStaffUserId()));
    }

    @Transactional
    public StaffInspectionDetailResponse updateStatus(AuthenticatedUser principal, Long id, String status) {
        // 员工端更新状态：只能更新本人上报
        // - 状态合法性使用白名单校验
        // - resolvedAt：仅在 RESOLVED 时写入（此处不主动清空，保持与原逻辑一致）
        Long staffUserId = requireUserId(principal);
        if (id == null || !StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id/status required");
        }

        String st = status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(st)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }

        StaffInspectionReport row = staffInspectionReportMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(row.getStaffUserId(), staffUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        row.setStatus(st);
        row.setUpdatedAt(LocalDateTime.now());
        if (Objects.equals(st, "RESOLVED")) {
            row.setResolvedAt(LocalDateTime.now());
        }
        staffInspectionReportMapper.updateById(row);
        return toDetail(row);
    }

    public StaffDailyReportResponse dailyReport(AuthenticatedUser principal, LocalDate date) {
        // 员工个人日报：跨模块聚合统计
        // - 统计区间采用 [start, end)（startOfDay 到 nextDayStart）
        // - 指标包含：片区场地状态、本人核销数、本人投诉处理数、本人巡检上报数、低库存器材数
        Long staffUserId = requireUserId(principal);
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date required");
        }

        StaffProfile profile = requireStaffProfile(staffUserId);
        if (!StringUtils.hasText(profile.getRegion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "staff region not configured");
        }
        String region = profile.getRegion().trim();

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        // start/end：半开区间 [start, end)
        // - 避免跨天边界数据被重复计入两天

        long venuesTotal = venueMapper.selectCount(new LambdaQueryWrapper<Venue>().eq(Venue::getArea, region));
        long venuesMaintenance = venueMapper.selectCount(new LambdaQueryWrapper<Venue>().eq(Venue::getArea, region).eq(Venue::getStatus, "MAINTENANCE"));
        long venuesDisabled = venueMapper.selectCount(new LambdaQueryWrapper<Venue>().eq(Venue::getArea, region).eq(Venue::getStatus, "DISABLED"));

        long myVenueVerifications = venueVerificationLogMapper.selectCount(new LambdaQueryWrapper<VenueVerificationLog>()
            .eq(VenueVerificationLog::getStaffUserId, staffUserId)
            .ge(VenueVerificationLog::getVerifiedAt, start)
            .lt(VenueVerificationLog::getVerifiedAt, end));

        long myComplaintsUpdated = complaintMapper.selectCount(new LambdaQueryWrapper<Complaint>()
            .eq(Complaint::getAssignedStaffId, staffUserId)
            .ge(Complaint::getUpdatedAt, start)
            .lt(Complaint::getUpdatedAt, end));

        long myComplaintsResolved = complaintMapper.selectCount(new LambdaQueryWrapper<Complaint>()
            .eq(Complaint::getAssignedStaffId, staffUserId)
            .isNotNull(Complaint::getResolvedAt)
            .ge(Complaint::getResolvedAt, start)
            .lt(Complaint::getResolvedAt, end));

        long myInspectionReports = staffInspectionReportMapper.selectCount(new LambdaQueryWrapper<StaffInspectionReport>()
            .eq(StaffInspectionReport::getStaffUserId, staffUserId)
            .ge(StaffInspectionReport::getCreatedAt, start)
            .lt(StaffInspectionReport::getCreatedAt, end));

        long equipmentLowStock = equipmentMapper.selectCount(new LambdaQueryWrapper<Equipment>() 
            .le(Equipment::getStock, LOW_STOCK_THRESHOLD)
            .eq(Equipment::getStatus, "ON_SALE"));

        StaffDailyReportResponse resp = new StaffDailyReportResponse();
        resp.setDate(date);
        resp.setStaffUserId(staffUserId);
        resp.setRegion(region);
        resp.setVenuesTotal(venuesTotal);
        resp.setVenuesMaintenance(venuesMaintenance);
        resp.setVenuesDisabled(venuesDisabled);
        resp.setMyVenueVerifications(myVenueVerifications);
        resp.setMyComplaintsUpdated(myComplaintsUpdated);
        resp.setMyComplaintsResolved(myComplaintsResolved);
        resp.setMyInspectionReports(myInspectionReports);
        resp.setEquipmentLowStock(equipmentLowStock);
        return resp;
    }

    private LambdaQueryWrapper<StaffInspectionReport> buildQuery(Long staffUserId, String status, String targetType) {
        // 构造员工端“我的上报”查询条件：
        // - staffUserId 固定为本人
        // - status/targetType 为可选过滤项
        LambdaQueryWrapper<StaffInspectionReport> qw = new LambdaQueryWrapper<StaffInspectionReport>().eq(StaffInspectionReport::getStaffUserId, staffUserId);
        if (StringUtils.hasText(status)) {
            qw.eq(StaffInspectionReport::getStatus, status.trim().toUpperCase());
        }
        if (StringUtils.hasText(targetType)) {
            qw.eq(StaffInspectionReport::getTargetType, targetType.trim().toUpperCase());
        }
        return qw;
    }

    private StaffInspectionListItem toListItem(StaffInspectionReport row) {
        return toListItem(row, null);
    }

    private StaffInspectionListItem toListItem(StaffInspectionReport row, String staffUsername) {
        if (row == null) {
            return new StaffInspectionListItem();
        }
        StaffInspectionListItem it = new StaffInspectionListItem();
        it.setId(row.getId());
        it.setStaffUserId(row.getStaffUserId());
        it.setStaffUsername(staffUsername);
        it.setRegion(row.getRegion());
        it.setTargetType(row.getTargetType());
        it.setVenueId(row.getVenueId());
        it.setEquipmentId(row.getEquipmentId());
        it.setIssueType(row.getIssueType());
        it.setContent(row.getContent());
        it.setStatus(row.getStatus());
        it.setCreatedAt(row.getCreatedAt());
        it.setUpdatedAt(row.getUpdatedAt());
        it.setResolvedAt(row.getResolvedAt());
        return it;
    }

    private StaffInspectionDetailResponse toDetail(StaffInspectionReport row) {
        return toDetail(row, null);
    }

    private StaffInspectionDetailResponse toDetail(StaffInspectionReport row, String staffUsername) {
        // 详情 DTO 组装：
        // - attachments 在这里从 JSON 字符串解析成 List<String>
        // - staffUsername 为展示字段：管理端查看时需要显示上报人用户名
        StaffInspectionDetailResponse resp = new StaffInspectionDetailResponse();
        resp.setId(row.getId());
        resp.setStaffUserId(row.getStaffUserId());
        resp.setStaffUsername(staffUsername);
        resp.setRegion(row.getRegion());
        resp.setTargetType(row.getTargetType());
        resp.setVenueId(row.getVenueId());
        resp.setEquipmentId(row.getEquipmentId());
        resp.setIssueType(row.getIssueType());
        resp.setContent(row.getContent());
        resp.setAttachments(parseJsonList(row.getAttachments()));
        resp.setStatus(row.getStatus());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        resp.setResolvedAt(row.getResolvedAt());
        return resp;
    }

    private Map<Long, String> loadUsernameMap(List<StaffInspectionReport> rows) {
        // 批量加载上报人用户名：staffUserId -> username
        // - 用于管理端列表页展示，避免对每条记录逐个 selectById（N+1 查询）
        Map<Long, String> out = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        List<Long> ids = new ArrayList<>();
        for (StaffInspectionReport r : rows) {
            if (r != null && r.getStaffUserId() != null) {
                ids.add(r.getStaffUserId());
            }
        }
        if (ids.isEmpty()) {
            return out;
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        if (users != null) {
            for (SysUser u : users) {
                if (u != null && u.getId() != null) {
                    out.put(u.getId(), u.getUsername());
                }
            }
        }
        return out;
    }

    private String loadUsername(Long userId) {
        // 单条加载用户名：用于管理端详情页
        // - 这里只有一条记录，直接 selectById 成本可接受
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? null : u.getUsername();
    }

    private List<String> parseJsonList(String json) {
        // JSON -> List<String>：用于解析 attachments
        // - 容错策略：历史数据可能不是合法 JSON（例如直接存了字符串），此处返回空列表避免接口 500
        // - 只接受数组格式（"[") 开头），避免把普通字符串误当作 JSON 解析
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        String s = json.trim();
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

    private String toJsonOrNull(List<String> list) {
        // List<String> -> JSON：用于存储 attachments
        // - null/空列表 => null（节省存储）
        // - 长度限制（2000）：避免异常大 payload 直接落库
        // - 序列化失败属于服务端异常（理论上不常见），按 500 返回
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(list);
            if (json.length() > 2000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attachments too long");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize attachments");
        }
    }

    private Long requireUserId(AuthenticatedUser principal) {
        // 统一认证校验：缺少登录态 => 401
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }

    private StaffProfile requireStaffProfile(Long staffUserId) {
        // 员工资料前置条件：
        // - 巡检上报需要 region（片区）字段用于管理端筛选与日报统计
        // - 若 staff_profile 未配置，直接返回 400，引导管理员先完善员工资料
        StaffProfile profile = staffProfileMapper.selectById(staffUserId);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "staff profile not configured");
        }
        return profile;
    }
}
