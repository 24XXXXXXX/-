package com.communitysport.equipment.controller;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.equipment.dto.AdminEquipmentCreateRequest;
import com.communitysport.equipment.dto.AdminEquipmentUpdateRequest;
import com.communitysport.equipment.dto.EquipmentDetailResponse;
import com.communitysport.equipment.dto.EquipmentPageResponse;
import com.communitysport.equipment.dto.EquipmentStatusUpdateRequest;
import com.communitysport.equipment.service.EquipmentAdminService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class EquipmentAdminController {

    private final EquipmentAdminService equipmentAdminService;

    public EquipmentAdminController(EquipmentAdminService equipmentAdminService) {
        this.equipmentAdminService = equipmentAdminService;
    }

    @GetMapping("/api/admin/equipments")
    public EquipmentPageResponse list(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status
    ) {
        // 管理端：后台商品列表。
        //
        // 权限边界：
        // - /api/admin/** 前缀语义上就是管理员接口，但仍在 Controller 做显式 requireAdmin
        // - 这样能在进入 Service 业务前快速拒绝未授权请求（贴近 HTTP 边界）
        //
        // 参数语义：
        // - page/size：分页（Service 内会做默认值与范围裁剪）
        // - categoryId/keyword/status：可选过滤条件
        requireAdmin(authentication);
        return equipmentAdminService.list(page, size, categoryId, keyword, status);
    }

    @GetMapping("/api/admin/equipments/{id}")
    public EquipmentDetailResponse detail(Authentication authentication, @PathVariable("id") Long id) {
        // 管理端：后台商品详情。
        // - 能查看上架/下架商品
        // - id 不存在返回 404
        requireAdmin(authentication);
        return equipmentAdminService.detail(id);
    }

    @PostMapping("/api/admin/equipments")
    public EquipmentDetailResponse create(Authentication authentication, @RequestBody AdminEquipmentCreateRequest request) {
        // 管理端：新增商品。
        //
        // 这里先 getPrincipal 再 requireAdmin 的原因：
        // - 两者都可能抛 401/403
        // - getPrincipal 能确保 Authentication 的 principal 是系统期望的 AuthenticatedUser
        // - Service 层会再次 requireUserId，避免内部调用绕过
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return equipmentAdminService.create(au, request);
    }

    @PutMapping("/api/admin/equipments/{id}")
    public EquipmentDetailResponse update(Authentication authentication, @PathVariable("id") Long id, @RequestBody AdminEquipmentUpdateRequest request) {
        // 管理端：编辑商品（部分字段更新）。
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return equipmentAdminService.update(au, id, request);
    }

    @PostMapping("/api/admin/equipments/{id}/status")
    public EquipmentDetailResponse updateStatus(Authentication authentication, @PathVariable("id") Long id, @RequestBody EquipmentStatusUpdateRequest request) {
        // 管理端：上/下架。
        // - 单独接口便于前端做“上架/下架”按钮
        // - 具体状态合法性与写入由 Service 统一处理
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return equipmentAdminService.updateStatus(au, id, request);
    }

    @DeleteMapping("/api/admin/equipments/{id}")
    public void delete(Authentication authentication, @PathVariable("id") Long id) {
        // 管理端：删除商品。
        // - 删除失败（例如被订单引用）在 Service 中会转换为 409（冲突）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        equipmentAdminService.delete(au, id);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 认证边界：
        // - 未登录直接 401
        // - principal 类型不匹配也视为未授权（防止安全上下文被污染）
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
        // 授权边界：必须具备 ROLE_ADMIN。
        //
        // 注意这里的异常语义：
        // - authentication 为空：401
        // - 已登录但无权限：403
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
