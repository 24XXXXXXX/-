package com.communitysport.withdraw.controller;

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

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.withdraw.dto.WithdrawItem;
import com.communitysport.withdraw.dto.WithdrawPageResponse;
import com.communitysport.withdraw.dto.WithdrawProcessRequest;
import com.communitysport.withdraw.service.CoachWithdrawService;

@RestController
public class AdminWithdrawController {

    // 管理端-教练提现审核 Controller：
    // - 提现申请由教练端提交（通常为 /api/coach/**）
    // - 管理端在这里提供：列表查询 + 审批通过/拒绝
    //
    // 权限：
    // - 所有接口都以 /api/admin 开头，仅允许 ROLE_ADMIN
    // - Controller 内显式 requireAdmin，属于“防御式校验”，让权限边界更直观

    private final CoachWithdrawService coachWithdrawService;

    public AdminWithdrawController(CoachWithdrawService coachWithdrawService) {
        this.coachWithdrawService = coachWithdrawService;
    }

    @GetMapping("/api/admin/withdraw-requests")
    public WithdrawPageResponse list(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "coachUserId", required = false) Long coachUserId,
            @RequestParam(name = "requestNo", required = false) String requestNo
    ) {
        // 管理端提现申请列表：支持分页 + 多条件筛选
        requireAdmin(authentication);
        return coachWithdrawService.adminList(page, size, status, coachUserId, requestNo);
    }

    @PostMapping("/api/admin/withdraw-requests/{id}/approve")
    public WithdrawItem approve(Authentication authentication, @PathVariable("id") Long id, @RequestBody(required = false) WithdrawProcessRequest request) {
        // 审批通过属于“资金/风控敏感操作”，显式取 principal 以便 Service 记录操作人（审计）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return coachWithdrawService.approve(au.userId(), id, request);
    }

    @PostMapping("/api/admin/withdraw-requests/{id}/reject")
    public WithdrawItem reject(Authentication authentication, @PathVariable("id") Long id, @RequestBody(required = false) WithdrawProcessRequest request) {
        // 审批拒绝：同样需要记录操作人（审计）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return coachWithdrawService.reject(au.userId(), id, request);
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
