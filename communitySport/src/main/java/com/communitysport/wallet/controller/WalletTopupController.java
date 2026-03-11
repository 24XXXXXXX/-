package com.communitysport.wallet.controller;

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
import com.communitysport.wallet.dto.WalletTopupCreateRequest;
import com.communitysport.wallet.dto.WalletTopupProcessRequest;
import com.communitysport.wallet.dto.WalletTopupRequestItem;
import com.communitysport.wallet.dto.WalletTopupRequestPageResponse;
import com.communitysport.wallet.service.WalletTopupService;

/**
 * 充值申请接口。
 *
 * <p>这组接口分为两类：
 * <p>1）用户侧：提交充值申请、查看我的申请
 * <p>2）管理员侧：查看所有申请、审批通过/拒绝
 *
 * <p>为什么管理员接口在这里要做 requireAdmin，而不是完全依赖 SecurityConfig？
 * <p>- SecurityConfig 会做“路由级别”的鉴权（例如 /api/admin/** 必须 ADMIN）
 * <p>- 这里再做一次显式校验，属于“业务层的防御式编程”：
 *   即使未来有人误改了 SecurityConfig 放行规则，Controller 也能兜底拒绝非管理员
 * <p>- 另外，这样读代码的人能更快理解：哪些接口是管理员专用
 */
@RestController
public class WalletTopupController {

    // 充值申请 Controller：
    // - 用户端：提交充值申请、查看我的申请列表
    // - 管理端：查看所有申请、审批通过/拒绝（属于资金敏感操作）
    //
    // 说明：
    // - 管理端接口以 /api/admin 开头，依赖路由鉴权的同时，Controller 再显式 requireAdmin（防御式校验）
    // - approve 会触发钱包入账与流水写入；reject 不发生资金变动

    private final WalletTopupService walletTopupService;

    public WalletTopupController(WalletTopupService walletTopupService) {
        this.walletTopupService = walletTopupService;
    }

    @PostMapping("/api/wallet/topups")
    public WalletTopupRequestItem create(Authentication authentication, @RequestBody WalletTopupCreateRequest request) {
        // 用户提交充值申请（不会立刻入账，只生成 PENDING 申请单）
        return walletTopupService.create(getPrincipal(authentication), request);
    }

    @GetMapping("/api/wallet/topups")
    public WalletTopupRequestPageResponse myTopups(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        // 用户查看自己的充值申请列表（分页 + 可选状态筛选）
        return walletTopupService.myRequests(getPrincipal(authentication), page, size, status);
    }

    @GetMapping("/api/admin/wallet/topups")
    public WalletTopupRequestPageResponse adminList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "requestNo", required = false) String requestNo
    ) {
        // 管理员查看所有充值申请（支持多条件筛选）
        requireAdmin(authentication);
        return walletTopupService.adminList(page, size, status, userId, requestNo);
    }

    @PostMapping("/api/admin/wallet/topups/{id}/approve")
    public WalletTopupRequestItem approve(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody(required = false) WalletTopupProcessRequest request
    ) {
        // 管理员审批通过：会触发 walletService.credit 入账 + 写 TOPUP 流水
        // - 属于资金敏感操作，需要记录操作人（adminUserId）用于审计
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return walletTopupService.approve(au.userId(), id, request);
    }

    @PostMapping("/api/admin/wallet/topups/{id}/reject")
    public WalletTopupRequestItem reject(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody(required = false) WalletTopupProcessRequest request
    ) {
        // 管理员拒绝：只改申请状态，不发生资金变动
        // - 同样需要记录操作人用于审计
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return walletTopupService.reject(au.userId(), id, request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 从 Spring Security 上下文中获取当前登录用户。
        // JwtAuthenticationFilter 会把 userId/username 写入 principal。
        //
        // 异常语义：
        // - authentication/principal 为空或类型不匹配 => 401
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
        // 业务侧显式检查 ADMIN 角色。
        // 注意：角色字符串约定为 ROLE_ADMIN（与 Spring Security 的 GrantedAuthority 规范一致）。
        //
        // 异常语义：
        // - 401：未登录
        // - 403：已登录但无管理员权限
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
