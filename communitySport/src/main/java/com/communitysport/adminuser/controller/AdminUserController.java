package com.communitysport.adminuser.controller;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.adminuser.dto.AdminUserCreateRequest;
import com.communitysport.adminuser.dto.AdminUserDetailResponse;
import com.communitysport.adminuser.dto.AdminUserPageResponse;
import com.communitysport.adminuser.dto.AdminUserPasswordResetRequest;
import com.communitysport.adminuser.dto.AdminUserRolesUpdateRequest;
import com.communitysport.adminuser.dto.AdminUserStatusUpdateRequest;
import com.communitysport.adminuser.dto.AdminUserUpdateRequest;
import com.communitysport.adminuser.dto.StaffProfileUpdateRequest;
import com.communitysport.adminuser.service.AdminUserService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class AdminUserController {

    // 管理端-用户管理 Controller：
    // - 该模块提供管理员对用户的“查/改/创建/禁用/重置密码/分配角色/维护员工资料”等能力
    // - 所有接口均以 /api/admin 开头，属于强 RBAC 接口，只允许 ROLE_ADMIN
    //
    // 说明：
    // - 项目中通常同时依赖 SecurityConfig 的路由鉴权 + Controller 内显式 requireAdmin
    //   这种“防御式校验”可以让权限边界更直观，也避免未来路由配置误改导致的越权

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/api/admin/users")
    public AdminUserPageResponse list(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "role", required = false) String role
    ) {
        requireAdmin(authentication);
        return adminUserService.list(page, size, keyword, status, role);
    }

    @GetMapping("/api/admin/users/{id}")
    public AdminUserDetailResponse detail(Authentication authentication, @PathVariable("id") Long id) {
        requireAdmin(authentication);
        return adminUserService.detail(id);
    }

    @PostMapping("/api/admin/users")
    public AdminUserDetailResponse create(Authentication authentication, @RequestBody AdminUserCreateRequest request) {
        // 创建用户属于“审计敏感操作”，因此显式拿到 principal（当前操作人）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return adminUserService.create(au, request);
    }

    @PutMapping("/api/admin/users/{id}")
    public AdminUserDetailResponse update(Authentication authentication, @PathVariable("id") Long id, @RequestBody AdminUserUpdateRequest request) {
        // 更新用户基本信息：同样记录操作人（审计/追责）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return adminUserService.update(au, id, request);
    }

    @PostMapping("/api/admin/users/{id}/status")
    public AdminUserDetailResponse updateStatus(Authentication authentication, @PathVariable("id") Long id, @RequestBody AdminUserStatusUpdateRequest request) {
        // 启用/禁用用户：属于权限与业务风险较高的操作
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return adminUserService.updateStatus(au, id, request);
    }

    @PostMapping("/api/admin/users/{id}/password")
    public AdminUserDetailResponse resetPassword(Authentication authentication, @PathVariable("id") Long id, @RequestBody AdminUserPasswordResetRequest request) {
        // 重置密码：管理员强制操作
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return adminUserService.resetPassword(au, id, request);
    }

    @PostMapping("/api/admin/users/{id}/roles")
    public AdminUserDetailResponse updateRoles(Authentication authentication, @PathVariable("id") Long id, @RequestBody AdminUserRolesUpdateRequest request) {
        // 角色变更：影响用户可访问的功能范围
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return adminUserService.updateRoles(au, id, request);
    }

    @PostMapping("/api/admin/users/{id}/staff-profile")
    public AdminUserDetailResponse updateStaffProfile(Authentication authentication, @PathVariable("id") Long id, @RequestBody StaffProfileUpdateRequest request) {
        // 员工资料维护：只允许对 STAFF 角色用户维护 staff_profile
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return adminUserService.updateStaffProfile(au, id, request);
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
        //
        // 说明：这里采用“手写遍历 authorities”的方式做 RBAC，
        // 与其它业务模块保持一致（便于阅读、排查和统一异常语义）。
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
