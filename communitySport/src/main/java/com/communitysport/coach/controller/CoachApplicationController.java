package com.communitysport.coach.controller;

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

import com.communitysport.coach.dto.CoachApplicationItem;
import com.communitysport.coach.dto.CoachApplicationPageResponse;
import com.communitysport.coach.dto.CoachApplicationProcessRequest;
import com.communitysport.coach.dto.CoachApplicationSubmitRequest;
import com.communitysport.coach.service.CoachApplicationService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CoachApplicationController {

    // 教练入驻申请 Controller：
    // - 用户端：提交入驻申请、查看我的申请
    // - 管理端：按条件分页查看所有申请、审批通过/拒绝
    //
    // 说明：
    // - 管理端接口以 /api/admin 开头，属于强 RBAC，仅允许 ROLE_ADMIN
    // - 审批操作属于“权限敏感/审计敏感”，因此显式取 principal 传递给 Service 记录操作人

    private final CoachApplicationService coachApplicationService;

    public CoachApplicationController(CoachApplicationService coachApplicationService) {
        this.coachApplicationService = coachApplicationService;
    }

    @PostMapping("/api/coach/applications")
    public CoachApplicationItem submit(Authentication authentication, @RequestBody CoachApplicationSubmitRequest request) {
        // 用户提交教练入驻申请：Service 内会做登录态校验与内容校验
        return coachApplicationService.submit(getPrincipal(authentication), request);
    }

    @GetMapping("/api/coach/applications/me")
    public CoachApplicationItem my(Authentication authentication) {
        // 用户查看“我的申请”：
        // - 若未提交过申请，返回值如何表现由 Service 决定
        return coachApplicationService.myApplication(getPrincipal(authentication));
    }

    @GetMapping("/api/admin/coach/applications")
    public CoachApplicationPageResponse adminList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "userId", required = false) Long userId
    ) {
        // 管理端申请列表：分页 + 可选条件筛选
        requireAdmin(authentication);
        return coachApplicationService.adminList(page, size, status, userId);
    }

    @PostMapping("/api/admin/coach/applications/{id}/approve")
    public CoachApplicationItem approve(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody(required = false) CoachApplicationProcessRequest request
    ) {
        // 管理端审批通过：
        // - 会改变申请状态，并可能为用户赋予 COACH 角色（由 Service 实现）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return coachApplicationService.approve(au.userId(), id, request);
    }

    @PostMapping("/api/admin/coach/applications/{id}/reject")
    public CoachApplicationItem reject(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody(required = false) CoachApplicationProcessRequest request
    ) {
        // 管理端审批拒绝：只改变申请状态（具体口径由 Service 控制）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return coachApplicationService.reject(au.userId(), id, request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 统一 principal 提取：
        // - 未登录/未携带 token/无法解析 principal => 401
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
        // - 401：未登录
        // - 403：已登录但不具备管理员角色
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
}
