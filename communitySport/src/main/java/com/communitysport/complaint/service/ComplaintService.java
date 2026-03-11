package com.communitysport.complaint.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.complaint.dto.ComplaintAssignRequest;
import com.communitysport.complaint.dto.ComplaintCreateRequest;
import com.communitysport.complaint.dto.ComplaintDetailResponse;
import com.communitysport.complaint.dto.ComplaintListItem;
import com.communitysport.complaint.dto.ComplaintMessageCreateRequest;
import com.communitysport.complaint.dto.ComplaintMessageItem;
import com.communitysport.complaint.dto.ComplaintPageResponse;
import com.communitysport.complaint.dto.ComplaintStatusUpdateRequest;
import com.communitysport.complaint.entity.Complaint;
import com.communitysport.complaint.entity.ComplaintMessage;
import com.communitysport.complaint.mapper.ComplaintMapper;
import com.communitysport.complaint.mapper.ComplaintMessageMapper;
import com.communitysport.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ComplaintService {

    // 投诉/工单模块核心服务：
    // - 用户端：创建投诉、查看我的投诉、在工单下追加消息
    // - 管理端：全量查询/筛选、指派处理人、追加消息、更新状态
    // - 员工端：查看指派给我/可领取的工单、追加消息、更新状态
    //
    // 关键设计点：
    // - 类型白名单：complaintType 只允许固定集合（ALLOWED_TYPES），避免前端随意传值污染统计口径
    // - 附件存储：attachments 在表中以 JSON 字符串保存，接口层以 List<String> 读写
    // - 状态机：SUBMITTED -> (ASSIGNED) -> IN_PROGRESS -> RESOLVED
    //   - 追加消息可能触发状态回退/推进：例如 RESOLVED 后用户追加消息会拉回 IN_PROGRESS
    // - “指派/领取”语义：
    //   - 管理端 assign 明确指定 assignedStaffId
    //   - 员工端在未指派的 SUBMITTED 工单上回复，可能会把自己设置为 assignedStaffId（领取）
    // - 并发控制：部分 update 使用 where 条件约束（例如 ne(status, RESOLVED)）避免已解决工单被重复指派
    // - 性能：列表/详情需要显示用户名，采用批量查询 + Map 组装，避免 N+1

    private static final Set<String> ALLOWED_TYPES = Set.of("VENUE", "EQUIPMENT", "COURSE", "OTHER");

    // ALLOWED_TYPES：投诉类型白名单
    // - 统一统计口径（例如按模块分类统计投诉量）
    // - 避免任意字符串导致数据不可控/前端显示混乱

    private final ComplaintMapper complaintMapper;

    private final ComplaintMessageMapper complaintMessageMapper;

    private final SysUserMapper sysUserMapper;

    private final ObjectMapper objectMapper;

    public ComplaintService(
            ComplaintMapper complaintMapper,
            ComplaintMessageMapper complaintMessageMapper,
            SysUserMapper sysUserMapper,
            ObjectMapper objectMapper
    ) {
        this.complaintMapper = complaintMapper;
        this.complaintMessageMapper = complaintMessageMapper;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ComplaintDetailResponse create(AuthenticatedUser principal, ComplaintCreateRequest request) {
        // 用户创建投诉：
        // - complaintType/content 必填
        // - attachments（可选）会序列化为 JSON 字符串存库
        // - 初始状态 SUBMITTED，未指派处理人
        Long userId = requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getComplaintType()) || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "complaintType/content required");
        }

        String type = request.getComplaintType().trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "complaintType invalid");
        }

        String content = request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }

        String attachmentsJson = toJsonOrNull(request.getAttachments());

        // attachmentsJson：将附件 URL 列表序列化为 JSON
        // - 存库时保持结构化；出参时再反序列化为 List<String>

        LocalDateTime now = LocalDateTime.now();
        Complaint row = new Complaint();
        row.setComplaintNo(UUID.randomUUID().toString().replace("-", ""));

        // complaintNo：工单对外单号
        // - 生成后稳定不变，适合在管理端筛选/查询时使用
        // - 相比自增 id，更适合暴露给前端或用于对账/沟通

        row.setUserId(userId);
        row.setComplaintType(type);
        row.setContent(content);
        row.setAttachments(attachmentsJson);
        row.setStatus("SUBMITTED");
        row.setAssignedStaffId(null);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        // updatedAt：最后一次变更时间
        // - 创建、追加消息、指派、状态变更都会刷新
        // - 用于后台列表“最近更新”排序/筛选

        row.setResolvedAt(null);
        complaintMapper.insert(row);

        return myDetail(principal, row.getId());
    }

    public ComplaintPageResponse myComplaints(AuthenticatedUser principal, Integer page, Integer size, String status) {
        // 用户端：我的投诉分页
        // - 只查询 user_id = 当前用户的工单
        // - status 可选筛选
        Long userId = requireUserId(principal);

        int p = normalizePage(page);
        int s = normalizeSize(size);

        LambdaQueryWrapper<Complaint> countQw = new LambdaQueryWrapper<Complaint>().eq(Complaint::getUserId, userId);
        if (StringUtils.hasText(status)) {
            countQw.eq(Complaint::getStatus, status.trim());
        }
        long total = complaintMapper.selectCount(countQw);

        // total：总条数必须与 listQw 的筛选条件一致
        // - 否则前端会出现“列表页数与总数不匹配”的分页错乱

        long offset = (long) (p - 1) * s;
        List<ComplaintListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<Complaint> listQw = new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getUserId, userId);
            if (StringUtils.hasText(status)) {
                listQw.eq(Complaint::getStatus, status.trim());
            }
            listQw.orderByDesc(Complaint::getId).last("LIMIT " + s + " OFFSET " + offset);

            List<Complaint> rows = complaintMapper.selectList(listQw);
            // 批量查询用户名/处理人用户名，避免 N+1
            Map<Long, String> userMap = loadUsernames(rows);
            Map<Long, String> staffMap = loadStaffUsernames(rows);
            for (Complaint c : rows) {
                Long uid = c == null ? null : c.getUserId();
                Long staffId = c == null ? null : c.getAssignedStaffId();
                String username = uid == null ? null : userMap.get(uid);
                String staffUsername = (staffId == null || Objects.equals(staffId, 0L)) ? null : staffMap.get(staffId);
                items.add(toListItem(c, username, staffUsername));
            }
        }

        ComplaintPageResponse resp = new ComplaintPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public ComplaintDetailResponse myDetail(AuthenticatedUser principal, Long id) {
        // 用户端：投诉详情
        // - 资源级别校验：只能查看自己的投诉
        Long userId = requireUserId(principal);
        Complaint c = requireComplaint(id);
        if (!Objects.equals(c.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return buildDetail(c);
    }

    @Transactional
    public ComplaintMessageItem userAddMessage(AuthenticatedUser principal, Long id, ComplaintMessageCreateRequest request) {
        // 用户端：追加消息
        // - 只能对自己的工单追加
        // - 若工单已 RESOLVED，则追加消息会把状态拉回 IN_PROGRESS，并清空 resolvedAt
        Long userId = requireUserId(principal);
        Complaint c = requireComplaint(id);
        if (!Objects.equals(c.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String content = normalizeMessageContent(request);
        String attachmentsJson = toJsonOrNull(request == null ? null : request.getAttachments());

        LocalDateTime now = LocalDateTime.now();

        ComplaintMessage msg = new ComplaintMessage();
        msg.setComplaintId(c.getId());
        msg.setSenderUserId(userId);
        msg.setSenderRole("USER");
        msg.setContent(content);
        msg.setAttachments(attachmentsJson);
        msg.setCreatedAt(now);
        complaintMessageMapper.insert(msg);

        Complaint upd = new Complaint();
        upd.setUpdatedAt(now);
        if (Objects.equals(c.getStatus(), "RESOLVED")) {
            // 已解决后再追加消息：视为重新打开工单

            // 状态机回退说明：
            // - RESOLVED 并不代表永远关闭；用户再次反馈时需要回到处理中
            // - resolvedAt 同时清空，避免报表把“重新打开的工单”仍算作已解决
            upd.setStatus("IN_PROGRESS");
            upd.setResolvedAt(null);
        }
        complaintMapper.update(upd, new LambdaQueryWrapper<Complaint>().eq(Complaint::getId, c.getId()));

        return toMessageItem(msg, principal.username());
    }

    public ComplaintPageResponse adminList(Integer page, Integer size, String status, String complaintType, Long userId, Long staffUserId, String complaintNo) {
        // 管理端：全量投诉列表（支持多条件筛选）
        // - complaintType/userId/staffUserId/complaintNo 均为可选过滤项
        int p = normalizePage(page);
        int s = normalizeSize(size);

        LambdaQueryWrapper<Complaint> countQw = buildAdminQuery(status, complaintType, userId, staffUserId, complaintNo);
        long total = complaintMapper.selectCount(countQw);

        // 管理端分页同样遵循：count/list 必须复用同一套条件构造（buildAdminQuery）

        long offset = (long) (p - 1) * s;
        List<ComplaintListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<Complaint> listQw = buildAdminQuery(status, complaintType, userId, staffUserId, complaintNo)
                .orderByDesc(Complaint::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<Complaint> rows = complaintMapper.selectList(listQw);
            Map<Long, String> userMap = loadUsernames(rows);
            Map<Long, String> staffMap = loadStaffUsernames(rows);
            for (Complaint c : rows) {
                Long uid = c == null ? null : c.getUserId();
                Long assignedId = c == null ? null : c.getAssignedStaffId();
                String username = uid == null ? null : userMap.get(uid);
                String staffUsername = (assignedId == null || Objects.equals(assignedId, 0L)) ? null : staffMap.get(assignedId);
                items.add(toListItem(c, username, staffUsername));
            }
        }

        ComplaintPageResponse resp = new ComplaintPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public ComplaintDetailResponse adminDetail(Long id) {
        Complaint c = requireComplaint(id);
        return buildDetail(c);
    }

    @Transactional
    public ComplaintMessageItem adminAddMessage(AuthenticatedUser principal, Long id, ComplaintMessageCreateRequest request) {
        // 管理端：追加消息
        // - 若未指派处理人，则默认把自己设置为 assignedStaffId（相当于“接单”）
        // - SUBMITTED/ASSIGNED 状态下追加消息会推动进入 IN_PROGRESS
        Long adminUserId = requireUserId(principal);
        Complaint c = requireComplaint(id);

        String content = normalizeMessageContent(request);
        String attachmentsJson = toJsonOrNull(request == null ? null : request.getAttachments());

        LocalDateTime now = LocalDateTime.now();
        ComplaintMessage msg = new ComplaintMessage();
        msg.setComplaintId(c.getId());
        msg.setSenderUserId(adminUserId);
        msg.setSenderRole("ADMIN");
        msg.setContent(content);
        msg.setAttachments(attachmentsJson);
        msg.setCreatedAt(now);
        complaintMessageMapper.insert(msg);

        Complaint upd = new Complaint();
        upd.setUpdatedAt(now);
        Long assignedStaffId = c.getAssignedStaffId();
        boolean unassigned = assignedStaffId == null || Objects.equals(assignedStaffId, 0L);
        if (unassigned) {
            // 未指派时管理员先回复：默认由管理员“接手处理”
            upd.setAssignedStaffId(adminUserId);
        }
        if (Objects.equals(c.getStatus(), "SUBMITTED") || Objects.equals(c.getStatus(), "ASSIGNED")) {
            upd.setStatus("IN_PROGRESS");
            upd.setResolvedAt(null);
        }
        complaintMapper.update(upd, new LambdaQueryWrapper<Complaint>().eq(Complaint::getId, c.getId()));

        return toMessageItem(msg, principal.username());
    }

    @Transactional
    public ComplaintDetailResponse adminUpdateStatus(AuthenticatedUser principal, Long id, ComplaintStatusUpdateRequest request) {
        // 管理端：更新状态
        // - 允许目标状态：IN_PROGRESS / RESOLVED
        // - 对状态机流转做校验：只能从 SUBMITTED/ASSIGNED/IN_PROGRESS 进入 RESOLVED 或保持处理中
        Long adminUserId = requireUserId(principal);
        if (id == null || request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }
        String target = request.getStatus().trim();
        if (!Set.of("IN_PROGRESS", "RESOLVED").contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
        }

        Complaint c = requireComplaint(id);
        if (Objects.equals(c.getStatus(), "RESOLVED") && Objects.equals(target, "RESOLVED")) {
            return adminDetail(id);
        }
        if (!Set.of("SUBMITTED", "ASSIGNED", "IN_PROGRESS").contains(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status transition");
        }

        LocalDateTime now = LocalDateTime.now();
        Complaint upd = new Complaint();
        Long assignedStaffId = c.getAssignedStaffId();
        boolean unassigned = assignedStaffId == null || Objects.equals(assignedStaffId, 0L);
        if (unassigned) {
            upd.setAssignedStaffId(adminUserId);
        }
        upd.setStatus(target);
        upd.setUpdatedAt(now);
        if (Objects.equals(target, "RESOLVED")) {
            upd.setResolvedAt(now);
        }
        complaintMapper.update(upd, new LambdaQueryWrapper<Complaint>().eq(Complaint::getId, c.getId()));
        return adminDetail(id);
    }

    @Transactional
    public ComplaintDetailResponse adminAssign(Long adminUserId, Long id, ComplaintAssignRequest request) {
        // 管理端：指派处理人
        // - 若工单已 RESOLVED 则不允许指派
        // - update where 条件额外约束 ne(status, RESOLVED)，避免并发下已解决的工单被覆盖指派
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null || request == null || request.getStaffUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "staffUserId required");
        }

        Complaint c = requireComplaint(id);
        if (Objects.equals(c.getStatus(), "RESOLVED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "complaint already resolved");
        }

        LocalDateTime now = LocalDateTime.now();
        Complaint upd = new Complaint();
        upd.setAssignedStaffId(request.getStaffUserId());
        if (Objects.equals(c.getStatus(), "IN_PROGRESS")) {
            upd.setStatus("IN_PROGRESS");
        } else {
            upd.setStatus("ASSIGNED");
        }
        upd.setUpdatedAt(now);

        int updated = complaintMapper.update(
            upd,
            new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getId, id)
                .ne(Complaint::getStatus, "RESOLVED")
        );
        // updated==0 常见原因：
        // - 并发下工单刚好被解决
        // - id 不存在（前面 requireComplaint 已拦）
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assign failed");
        }

        return adminDetail(id);
    }

    public ComplaintPageResponse staffMyComplaints(AuthenticatedUser principal, Integer page, Integer size, String status) {
        // 员工端：我的工单列表
        // - 默认只看 assigned_staff_id = 自己
        // - 特殊：当 status=ASSIGNED 时，会把“未指派且 SUBMITTED”也纳入（作为可领取池）
        Long staffId = requireUserId(principal);

        int p = normalizePage(page);
        int s = normalizeSize(size);

        LambdaQueryWrapper<Complaint> countQw = new LambdaQueryWrapper<Complaint>();
        if (StringUtils.hasText(status)) {
            String st = status.trim();
            if (Objects.equals(st, "ASSIGNED")) {
                countQw.in(Complaint::getStatus, "SUBMITTED", "ASSIGNED")
                    .and(qw -> qw.eq(Complaint::getAssignedStaffId, staffId)
                        .or().isNull(Complaint::getAssignedStaffId)
                        .or().eq(Complaint::getAssignedStaffId, 0L));
            } else {
                countQw.eq(Complaint::getStatus, st)
                    .eq(Complaint::getAssignedStaffId, staffId);
            }
        } else {
            countQw.eq(Complaint::getAssignedStaffId, staffId);
        }
        long total = complaintMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<ComplaintListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<Complaint> listQw = new LambdaQueryWrapper<Complaint>();
            if (StringUtils.hasText(status)) {
                String st = status.trim();
                if (Objects.equals(st, "ASSIGNED")) {
                    // 约定：ASSIGNED 视角包含两类：
                    // 1) 已指派给我且状态为 ASSIGNED
                    // 2) 未指派且状态为 SUBMITTED（可领取）
                    listQw.in(Complaint::getStatus, "SUBMITTED", "ASSIGNED")
                        .and(qw -> qw.eq(Complaint::getAssignedStaffId, staffId)
                            .or().isNull(Complaint::getAssignedStaffId)
                            .or().eq(Complaint::getAssignedStaffId, 0L));
                } else {
                    listQw.eq(Complaint::getStatus, st)
                        .eq(Complaint::getAssignedStaffId, staffId);
                }
            } else {
                listQw.eq(Complaint::getAssignedStaffId, staffId);
            }
            listQw.orderByDesc(Complaint::getId).last("LIMIT " + s + " OFFSET " + offset);

            List<Complaint> rows = complaintMapper.selectList(listQw);
            Map<Long, String> userMap = loadUsernames(rows);
            Map<Long, String> staffMap = loadStaffUsernames(rows);
            for (Complaint c : rows) {
                Long uid = c == null ? null : c.getUserId();
                Long assignedId = c == null ? null : c.getAssignedStaffId();
                String username = uid == null ? null : userMap.get(uid);
                String staffUsername = (assignedId == null || Objects.equals(assignedId, 0L)) ? null : staffMap.get(assignedId);
                items.add(toListItem(c, username, staffUsername));
            }
        }

        ComplaintPageResponse resp = new ComplaintPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public ComplaintDetailResponse staffDetail(AuthenticatedUser principal, Long id) {
        // 员工端：工单详情
        // - 已指派：只能被指派人查看
        // - 未指派：仅当状态仍为 SUBMITTED（可领取池）时允许查看
        Long staffId = requireUserId(principal);
        Complaint c = requireComplaint(id);
        Long assignedStaffId = c.getAssignedStaffId();
        boolean unassigned = assignedStaffId == null || Objects.equals(assignedStaffId, 0L);
        if (!unassigned && !Objects.equals(assignedStaffId, staffId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (unassigned && !Objects.equals(c.getStatus(), "SUBMITTED")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return buildDetail(c);
    }

    @Transactional
    public ComplaintMessageItem staffAddMessage(AuthenticatedUser principal, Long id, ComplaintMessageCreateRequest request) {
        // 员工端：追加消息
        // - 若工单未指派且处于 SUBMITTED，员工回复视为“领取”：assignedStaffId 置为自己
        // - SUBMITTED/ASSIGNED 回复后进入 IN_PROGRESS
        // - update where 条件里允许“已指派给我/未指派”两种路径，减少并发下的越权覆盖
        Long staffId = requireUserId(principal);
        Complaint c = requireComplaint(id);
        Long assignedStaffId = c.getAssignedStaffId();
        boolean unassigned = assignedStaffId == null || Objects.equals(assignedStaffId, 0L);
        if (!unassigned && !Objects.equals(assignedStaffId, staffId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (unassigned && !Objects.equals(c.getStatus(), "SUBMITTED")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String content = normalizeMessageContent(request);
        String attachmentsJson = toJsonOrNull(request == null ? null : request.getAttachments());

        LocalDateTime now = LocalDateTime.now();
        ComplaintMessage msg = new ComplaintMessage();
        msg.setComplaintId(c.getId());
        msg.setSenderUserId(staffId);
        msg.setSenderRole("STAFF");
        msg.setContent(content);
        msg.setAttachments(attachmentsJson);
        msg.setCreatedAt(now);
        complaintMessageMapper.insert(msg);

        Complaint upd = new Complaint();
        upd.setUpdatedAt(now);
        if (unassigned) {
            // 领取：未指派工单由当前员工接手
            upd.setAssignedStaffId(staffId);
        }
        if (Objects.equals(c.getStatus(), "ASSIGNED") || Objects.equals(c.getStatus(), "SUBMITTED")) {
            upd.setStatus("IN_PROGRESS");
            upd.setResolvedAt(null);
        }
        complaintMapper.update(
            upd,
            new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getId, c.getId())
                .and(qw -> qw.eq(Complaint::getAssignedStaffId, staffId).or().isNull(Complaint::getAssignedStaffId).or().eq(Complaint::getAssignedStaffId, 0L))
        );

        return toMessageItem(msg, principal.username());
    }

    @Transactional
    public ComplaintDetailResponse staffUpdateStatus(AuthenticatedUser principal, Long id, ComplaintStatusUpdateRequest request) {
        // 员工端：更新状态
        // - 只能操作“指派给我”或“可领取且仍 SUBMITTED”的工单
        // - 目标状态仅允许 IN_PROGRESS/RESOLVED
        Long staffId = requireUserId(principal);
        if (id == null || request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }

        String target = request.getStatus().trim();
        if (!Set.of("IN_PROGRESS", "RESOLVED").contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
        }

        Complaint c = requireComplaint(id);
        Long assignedStaffId = c.getAssignedStaffId();
        boolean unassigned = assignedStaffId == null || Objects.equals(assignedStaffId, 0L);
        if (!unassigned && !Objects.equals(assignedStaffId, staffId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (unassigned && !Objects.equals(c.getStatus(), "SUBMITTED")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (Objects.equals(c.getStatus(), "RESOLVED") && Objects.equals(target, "RESOLVED")) {
            return staffDetail(principal, id);
        }

        if (!Set.of("SUBMITTED", "ASSIGNED", "IN_PROGRESS").contains(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status transition");
        }

        LocalDateTime now = LocalDateTime.now();
        Complaint upd = new Complaint();
        if (unassigned) {
            upd.setAssignedStaffId(staffId);
        }
        upd.setStatus(target);
        upd.setUpdatedAt(now);
        if (Objects.equals(target, "RESOLVED")) {
            upd.setResolvedAt(now);
        }

        complaintMapper.update(
            upd,
            new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getId, c.getId())
                .and(qw -> qw.eq(Complaint::getAssignedStaffId, staffId).or().isNull(Complaint::getAssignedStaffId).or().eq(Complaint::getAssignedStaffId, 0L))
        );
        return staffDetail(principal, id);
    }

    private ComplaintDetailResponse buildDetail(Complaint c) {
        // 详情 DTO 组装：
        // - 主表 + 指派人展示 + 附件解析 + 消息列表（按 id 升序）
        // - senderUsername 通过批量查 senderUserId 再 Map 组装，避免 N+1
        if (c == null) {
            return null;
        }

        SysUser user = c.getUserId() == null ? null : sysUserMapper.selectById(c.getUserId());
        SysUser staff = c.getAssignedStaffId() == null ? null : sysUserMapper.selectById(c.getAssignedStaffId());

        ComplaintDetailResponse resp = new ComplaintDetailResponse();
        resp.setId(c.getId());
        resp.setComplaintNo(c.getComplaintNo());
        resp.setUserId(c.getUserId());
        resp.setUsername(user == null ? null : user.getUsername());
        resp.setComplaintType(c.getComplaintType());
        resp.setContent(c.getContent());
        resp.setAttachments(parseJsonList(c.getAttachments()));
        resp.setStatus(c.getStatus());
        resp.setAssignedStaffId(c.getAssignedStaffId());
        resp.setAssignedStaffUsername(staff == null ? null : staff.getUsername());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setUpdatedAt(c.getUpdatedAt());
        resp.setResolvedAt(c.getResolvedAt());

        List<ComplaintMessage> msgs = complaintMessageMapper.selectList(
            new LambdaQueryWrapper<ComplaintMessage>()
                .eq(ComplaintMessage::getComplaintId, c.getId())
                .orderByAsc(ComplaintMessage::getId)
        );

        // msgs：该投诉下的消息列表
        // - 这里按 id 升序返回，便于前端直接按时间线渲染
        // - selectList 理论上不应返回 null，但这里仍采用防御式写法（后续代码对 null 做了判断）

        Set<Long> senderIds = msgs.stream().map(ComplaintMessage::getSenderUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> senderMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(senderIds);
            if (users != null) {
                for (SysUser u : users) {
                    if (u != null && u.getId() != null) {
                        senderMap.put(u.getId(), u.getUsername());
                    }
                }
            }
        }

        List<ComplaintMessageItem> items = new ArrayList<>();
        if (msgs != null) {
            for (ComplaintMessage m : msgs) {
                ComplaintMessageItem mi = toMessageItem(m, m.getSenderUserId() == null ? null : senderMap.get(m.getSenderUserId()));
                items.add(mi);
            }
        }
        resp.setMessages(items);
        return resp;
    }

    private Complaint requireComplaint(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        Complaint c = complaintMapper.selectById(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "complaint not found");
        }
        return c;
    }

    private ComplaintMessageItem toMessageItem(ComplaintMessage msg, String senderUsername) {
        if (msg == null) {
            return null;
        }
        ComplaintMessageItem item = new ComplaintMessageItem();
        item.setId(msg.getId());
        item.setComplaintId(msg.getComplaintId());
        item.setSenderUserId(msg.getSenderUserId());
        item.setSenderRole(msg.getSenderRole());
        item.setSenderUsername(senderUsername);
        item.setContent(msg.getContent());
        item.setAttachments(parseJsonList(msg.getAttachments()));
        item.setCreatedAt(msg.getCreatedAt());
        return item;
    }

    private ComplaintListItem toListItem(Complaint c, String username, String staffUsername) {
        if (c == null) {
            return null;
        }
        ComplaintListItem item = new ComplaintListItem();
        item.setId(c.getId());
        item.setComplaintNo(c.getComplaintNo());
        item.setUserId(c.getUserId());
        item.setUsername(username);
        item.setComplaintType(c.getComplaintType());
        item.setStatus(c.getStatus());
        item.setAssignedStaffId(c.getAssignedStaffId());
        item.setAssignedStaffUsername(staffUsername);
        item.setCreatedAt(c.getCreatedAt());
        item.setUpdatedAt(c.getUpdatedAt());
        item.setResolvedAt(c.getResolvedAt());
        return item;
    }

    private Map<Long, String> loadUsernames(List<Complaint> rows) {
        // 批量加载“投诉发起人”的用户名：userId -> username
        // - 用于列表页展示，避免对每条投诉逐个 selectById（N+1）
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = rows.stream().map(Complaint::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
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

    private Map<Long, String> loadStaffUsernames(List<Complaint> rows) {
        // 批量加载“指派处理人”的用户名：assignedStaffId -> username
        // - 注意：assignedStaffId 可能为 null/0（未指派），此处只对非空 id 做批量查询
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = rows.stream().map(Complaint::getAssignedStaffId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
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

    private LambdaQueryWrapper<Complaint> buildAdminQuery(String status, String complaintType, Long userId, Long staffUserId, String complaintNo) {
        // 管理端列表的查询条件构造：
        // - 将多个可选过滤项统一收敛为一个 LambdaQueryWrapper
        // - 便于 count/list 复用同一套条件，避免两处条件不一致导致分页 total 错乱
        LambdaQueryWrapper<Complaint> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(Complaint::getStatus, status.trim());
        }
        if (StringUtils.hasText(complaintType)) {
            qw.eq(Complaint::getComplaintType, complaintType.trim().toUpperCase());
        }
        if (userId != null) {
            qw.eq(Complaint::getUserId, userId);
        }
        if (staffUserId != null) {
            qw.eq(Complaint::getAssignedStaffId, staffUserId);
        }
        if (StringUtils.hasText(complaintNo)) {
            qw.eq(Complaint::getComplaintNo, complaintNo.trim());
        }
        return qw;
    }

    private String normalizeMessageContent(ComplaintMessageCreateRequest request) {
        // 统一的消息内容校验：
        // - trim 后不可为空
        // - 长度上限 2000
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }
        String content = request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }
        if (content.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content too long");
        }
        return content;
    }

    private String toJsonOrNull(List<String> list) {
        // 将附件 URL 列表序列化为 JSON
        // - 空列表/全空白 => null（节省存储）
        // - JSON 序列化失败 => 400
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        List<String> trimmed = new ArrayList<>();
        for (String s : list) {
            if (StringUtils.hasText(s)) {
                trimmed.add(s.trim());
            }
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(trimmed);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attachments invalid");
        }
    }

    private List<String> parseJsonList(String json) {
        // 将 JSON 字符串解析为 List<String>
        // - 解析异常时返回空列表（容错：避免历史脏数据导致详情 500）
        // - 同时做去重（uniq）以避免重复附件 URL
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            if (list == null) {
                return List.of();
            }
            Set<String> uniq = new HashSet<>();
            List<String> out = new ArrayList<>();
            for (String s : list) {
                if (StringUtils.hasText(s) && uniq.add(s)) {
                    out.add(s);
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private int normalizePage(Integer page) {
        // 分页参数收敛：page < 1 归一为 1
        int p = page == null ? 1 : page.intValue();
        if (p < 1) {
            p = 1;
        }
        return p;
    }

    private int normalizeSize(Integer size) {
        // 分页参数收敛：1 <= size <= 100
        int s = size == null ? 20 : size.intValue();
        if (s < 1) {
            s = 1;
        }
        if (s > 100) {
            s = 100;
        }
        return s;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        // 统一认证校验：缺少登录态 => 401
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
