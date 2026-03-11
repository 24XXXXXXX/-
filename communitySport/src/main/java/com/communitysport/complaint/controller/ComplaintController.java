package com.communitysport.complaint.controller;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.complaint.dto.ComplaintAssignRequest;
import com.communitysport.complaint.dto.ComplaintCreateRequest;
import com.communitysport.complaint.dto.ComplaintDetailResponse;
import com.communitysport.complaint.dto.ComplaintMessageCreateRequest;
import com.communitysport.complaint.dto.ComplaintMessageItem;
import com.communitysport.complaint.dto.ComplaintPageResponse;
import com.communitysport.complaint.dto.ComplaintStatusUpdateRequest;
import com.communitysport.complaint.service.ComplaintService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class ComplaintController {

    // 投诉（工单）模块 Controller：
    // - 用户端：创建投诉、查看我的投诉列表/详情、追加消息
    // - 管理端（ROLE_ADMIN）：全量列表/详情、追加消息、更新状态、指派处理人
    // - 员工端（ROLE_STAFF 或 ROLE_ADMIN）：查看“分配给我/可领取”的投诉、追加消息、更新状态
    //
    // 设计说明：
    // - 权限校验优先在 Controller 层完成（requireAdmin/requireStaff）
    //   让权限不足时尽早返回 403，避免进入 Service 做无谓查询
    // - Service 仍会做资源级别校验（例如是否本人投诉/是否被指派）作为最后一道防线

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping("/api/complaints")
    public ComplaintDetailResponse create(Authentication authentication, @RequestBody ComplaintCreateRequest request) {
        // 用户创建投诉：必须登录（principal required）
        return complaintService.create(getPrincipal(authentication), request);
    }

    @GetMapping("/api/complaints")
    public ComplaintPageResponse myComplaints(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        // 用户查看“我的投诉”列表：仅能看到自己提交的工单
        return complaintService.myComplaints(getPrincipal(authentication), page, size, status);
    }

    @GetMapping("/api/complaints/{id}")
    public ComplaintDetailResponse myDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 用户查看“我的投诉详情”：Service 会校验该投诉属于当前用户
        return complaintService.myDetail(getPrincipal(authentication), id);
    }

    @PostMapping("/api/complaints/{id}/messages")
    public ComplaintMessageItem userAddMessage(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ComplaintMessageCreateRequest request
    ) {
        // 用户追加消息：仅能对自己的投诉追加；若投诉已 RESOLVED，Service 会把状态拉回 IN_PROGRESS
        return complaintService.userAddMessage(getPrincipal(authentication), id, request);
    }

    @GetMapping("/api/admin/complaints")
    public ComplaintPageResponse adminList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "complaintType", required = false) String complaintType,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "staffUserId", required = false) Long staffUserId,
            @RequestParam(name = "complaintNo", required = false) String complaintNo
    ) {
        // 管理端全量列表：必须是 ROLE_ADMIN
        requireAdmin(authentication);
        return complaintService.adminList(page, size, status, complaintType, userId, staffUserId, complaintNo);
    }

    @GetMapping("/api/admin/complaints/{id}")
    public ComplaintDetailResponse adminDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 管理端详情：必须是 ROLE_ADMIN
        requireAdmin(authentication);
        return complaintService.adminDetail(id);
    }

    @PostMapping("/api/admin/complaints/{id}/messages")
    public ComplaintMessageItem adminAddMessage(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ComplaintMessageCreateRequest request
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        // 管理端追加消息：必须是 ROLE_ADMIN
        requireAdmin(authentication);
        return complaintService.adminAddMessage(au, id, request);
    }

    @PostMapping("/api/admin/complaints/{id}/status")
    public ComplaintDetailResponse adminUpdateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ComplaintStatusUpdateRequest request
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        // 管理端更新状态：必须是 ROLE_ADMIN
        requireAdmin(authentication);
        return complaintService.adminUpdateStatus(au, id, request);
    }

    @PostMapping("/api/admin/complaints/{id}/assign")
    public ComplaintDetailResponse assign(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ComplaintAssignRequest request
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        // 指派处理人：必须是 ROLE_ADMIN
        requireAdmin(authentication);
        return complaintService.adminAssign(au.userId(), id, request);
    }

    @GetMapping("/api/staff/complaints")
    public ComplaintPageResponse staffList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        // 员工端列表：ROLE_STAFF 或 ROLE_ADMIN。
        //
        // 说明：员工端这里展示的不是“全量投诉”，而是“与我相关/我可处理”的投诉集合。
        // - 典型情况：已指派给我的投诉；或处于可领取状态的投诉（具体规则在 Service）
        // - status 为可选过滤条件：由 Service 统一解释（例如 SUBMITTED/IN_PROGRESS/RESOLVED）
        requireStaff(authentication);
        return complaintService.staffMyComplaints(au, page, size, status);
    }

    @GetMapping("/api/staff/complaints/{id}")
    public ComplaintDetailResponse staffDetail(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser au = getPrincipal(authentication);
        // 员工端详情：用于查看某条投诉工单的完整信息。
        //
        // 权限/资源校验在 Service 中完成：
        // - 若投诉已指派给他人：应拒绝访问（避免越权查看）
        // - 若投诉未指派且仍处于 SUBMITTED：可能允许“查看并领取/占用”（取决于 Service 的实现策略）
        requireStaff(authentication);
        return complaintService.staffDetail(au, id);
    }

    @PostMapping("/api/staff/complaints/{id}/messages")
    public ComplaintMessageItem staffAddMessage(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ComplaintMessageCreateRequest request
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        // 员工追加消息：用于处理过程中的沟通记录。
        //
        // 业务要点：
        // - 追加消息本身是“处理动作”的一部分，通常需要记录操作人（au）
        // - Service 会处理“领取/占用”逻辑：
        //   若该投诉未指派且符合领取条件，可能会在追加消息时把 assignedStaffId 置为当前员工，
        //   从而避免多人同时处理同一工单。
        requireStaff(authentication);
        return complaintService.staffAddMessage(au, id, request);
    }

    @PostMapping("/api/staff/complaints/{id}/status")
    public ComplaintDetailResponse staffUpdateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ComplaintStatusUpdateRequest request
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        // 员工更新状态：用于推进工单状态机。
        //
        // 关键点：
        // - 员工不能随意更新任何投诉，只能操作“自己负责/自己已领取”的投诉
        // - 状态流转合法性（例如 SUBMITTED -> IN_PROGRESS -> RESOLVED）由 Service 统一校验
        // - 该接口同样属于审计敏感操作：需要携带操作人（au）用于记录处理轨迹
        requireStaff(authentication);
        return complaintService.staffUpdateStatus(au, id, request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 统一 principal 提取：未登录或 principal 类型不对 => 401
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        return au;
    }

    private void requireAdmin(Authentication authentication) {
        // 角色校验：必须拥有 ROLE_ADMIN
        // - 401：未登录（authentication 为空）
        // - 403：已登录但不具备管理员角色
        //
        // 说明：这里采用手写遍历 authorities 的方式做角色判断，
        // 而不是使用注解（例如 @PreAuthorize），主要是为了：
        // - 让权限边界在 Controller 入口处直观可见（教学/阅读友好）
        // - 与项目中其它模块保持一致的权限写法
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean ok = false;
        if (authorities != null) {
            for (GrantedAuthority a : authorities) {
                if (a == null) {
                    continue;
                }
                String auth = a.getAuthority();
                if (StringUtils.hasText(auth) && "ROLE_ADMIN".equals(auth)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private void requireStaff(Authentication authentication) {
        // 角色校验：ROLE_STAFF 或 ROLE_ADMIN
        // - 401：未登录
        // - 403：已登录但不具备员工/管理员角色
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean ok = false;
        if (authorities != null) {
            for (GrantedAuthority a : authorities) {
                if (a == null) {
                    continue;
                }
                String auth = a.getAuthority();
                if (StringUtils.hasText(auth) && ("ROLE_ADMIN".equals(auth) || "ROLE_STAFF".equals(auth))) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
