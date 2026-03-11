package com.communitysport.inspection.controller;

import java.time.LocalDate;
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

import com.communitysport.inspection.dto.StaffDailyReportResponse;
import com.communitysport.inspection.dto.StaffInspectionCreateRequest;
import com.communitysport.inspection.dto.StaffInspectionDetailResponse;
import com.communitysport.inspection.dto.StaffInspectionPageResponse;
import com.communitysport.inspection.dto.StaffInspectionStatusUpdateRequest;
import com.communitysport.inspection.service.StaffInspectionService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class StaffInspectionController {

    // 员工巡检（上报）模块 Controller：
    // - 员工端（ROLE_STAFF/ROLE_ADMIN）：
    //   - 创建巡检上报、查看我的上报列表/详情、更新上报状态、查看个人日报
    // - 管理端（ROLE_ADMIN）：
    //   - 全量巡检列表/详情、强制更新巡检状态
    //
    // 说明：
    // - Controller 负责做“认证/角色”边界拦截（401/403），业务校验与数据组装在 Service 完成
    // - Service 内仍会做资源级别校验（例如 detail/updateStatus 必须是本人上报）

    private final StaffInspectionService staffInspectionService;

    public StaffInspectionController(StaffInspectionService staffInspectionService) {
        this.staffInspectionService = staffInspectionService;
    }

    @PostMapping("/api/staff/inspections")
    public StaffInspectionDetailResponse create(Authentication authentication, @RequestBody StaffInspectionCreateRequest request) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        // 员工创建巡检上报：必须是员工/管理员登录态
        return staffInspectionService.create(au, request);
    }

    @GetMapping("/api/staff/inspections")
    public StaffInspectionPageResponse myReports(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "targetType", required = false) String targetType
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        // 员工查看“我的巡检上报”分页列表：只返回 staff_user_id = 当前用户的记录
        return staffInspectionService.myReports(au, page, size, status, targetType);
    }

    @GetMapping("/api/staff/inspections/{id}")
    public StaffInspectionDetailResponse detail(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        // 员工查看详情：Service 会校验该上报属于本人
        return staffInspectionService.detail(au, id);
    }

    @PostMapping("/api/staff/inspections/{id}/status")
    public StaffInspectionDetailResponse updateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody StaffInspectionStatusUpdateRequest request
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        // 员工更新状态：只能更新自己提交的上报；状态合法性由 Service 校验
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }
        return staffInspectionService.updateStatus(au, id, request.getStatus());
    }

    @GetMapping("/api/staff/daily-report")
    public StaffDailyReportResponse dailyReport(Authentication authentication, @RequestParam("date") LocalDate date) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        // 员工个人日报：聚合当天的场地状态、核销、投诉处理、巡检上报、低库存等统计指标
        return staffInspectionService.dailyReport(au, date);
    }

    @GetMapping("/api/admin/inspections")
    public StaffInspectionPageResponse adminList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "region", required = false) String region
    ) {
        // 管理端全量巡检列表：仅 ROLE_ADMIN 可访问
        requireAdmin(authentication);
        return staffInspectionService.adminList(page, size, status, targetType, region);
    }

    @GetMapping("/api/admin/inspections/{id}")
    public StaffInspectionDetailResponse adminDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 管理端详情：仅 ROLE_ADMIN 可访问
        requireAdmin(authentication);
        return staffInspectionService.adminDetail(id);
    }

    @PostMapping("/api/admin/inspections/{id}/status")
    public StaffInspectionDetailResponse adminUpdateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody StaffInspectionStatusUpdateRequest request
    ) {
        // 管理端强制更新状态：仅 ROLE_ADMIN 可访问
        requireAdmin(authentication);
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }
        return staffInspectionService.adminUpdateStatus(id, request.getStatus());
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

    private void requireStaff(Authentication authentication) {
        // 角色校验：ROLE_STAFF 或 ROLE_ADMIN
        // - 401：未登录
        // - 403：已登录但不具备员工/管理员角色
        //
        // 说明：这里使用“手写遍历 authorities”的方式做 RBAC，
        // 与项目中其它模块保持一致；同时让权限边界在入口处更直观。
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

    private void requireAdmin(Authentication authentication) {
        // 角色校验：必须拥有 ROLE_ADMIN
        // - 401：未登录
        // - 403：已登录但不具备管理员角色
        //
        // 说明：此处不使用注解（例如 @PreAuthorize），而是直接在 Controller 入口处显式校验。
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
