package com.communitysport.adminreport.controller;

import java.time.LocalDate;
import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.adminreport.dto.AdminMetricsResponse;
import com.communitysport.adminreport.service.AdminReportService;

/**
 * 管理员报表接口。
 *
 * <p>目前提供一个核心接口：/api/admin/metrics
 * <p>用于管理员端仪表盘按日期区间拉取指标汇总。
 *
 * <p>权限：仅 ADMIN 可访问。
 * <p>这里既依赖 Spring Security 的路由鉴权，也在 Controller 再做一次 requireAdmin，
 * 属于“业务层防御式校验”。
 */
@RestController
public class AdminReportController {

    // 管理端-仪表盘/报表 Controller：
    // - 当前主要提供“指标汇总”接口（/api/admin/metrics），给管理员 Dashboard 使用
    // - 特点：只返回统计数字（聚合结果），不返回明细列表
    //
    // 权限：
    // - 强 RBAC：仅允许 ROLE_ADMIN
    // - 除 SecurityConfig 路由鉴权外，Controller 入口处再做一次 requireAdmin（防御式校验）

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @GetMapping("/api/admin/metrics")
    public AdminMetricsResponse metrics(
            Authentication authentication,
            @RequestParam(name = "startDate") LocalDate startDate,
            @RequestParam(name = "endDate") LocalDate endDate
    ) {
        // 指标查询接口：
        // - startDate/endDate 使用 yyyy-MM-dd
        // - 语义是“包含起止日期”（具体转换为时间区间在 service 中处理）
        // - 返回的是汇总指标，通常用于前端大盘卡片/趋势图
        requireAdmin(authentication);
        return adminReportService.metrics(startDate, endDate);
    }

    private void requireAdmin(Authentication authentication) {
        // 显式校验管理员角色：ROLE_ADMIN。
        // - 401：未登录
        // - 403：已登录但无管理员权限
        //
        // 说明：采用“手写遍历 authorities”与项目其他模块保持一致，便于统一异常语义与排查。
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
