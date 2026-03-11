package com.communitysport.sysconfig.controller;

import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.sysconfig.dto.SysConfigItem;
import com.communitysport.sysconfig.dto.SysConfigUpsertRequest;
import com.communitysport.sysconfig.service.SysConfigService;

@RestController
public class SysConfigAdminController {

    // 管理端-系统配置 Controller：
    // - 用于管理员维护“键值型配置”（sys_config 表）
    // - 常见用途：前端展示开关、运营文案、默认参数等
    //
    // 权限：
    // - /api/admin/sys-configs 仅允许 ROLE_ADMIN
    // - Controller 内显式 requireAdmin：防御式校验 + 权限边界更直观

    private final SysConfigService sysConfigService;

    public SysConfigAdminController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("/api/admin/sys-configs")
    public List<SysConfigItem> list(Authentication authentication, @RequestParam(name = "keyword", required = false) String keyword) {
        // 管理端配置列表：可选 keyword 模糊匹配（具体匹配规则在 Service/Mapper）
        requireAdmin(authentication);
        return sysConfigService.list(keyword);
    }

    @PostMapping("/api/admin/sys-configs")
    public SysConfigItem upsert(Authentication authentication, @RequestBody SysConfigUpsertRequest request) {
        // upsert：
        // - 若 key 已存在则更新 value
        // - 若 key 不存在则新增
        // - 这种写法便于前端“保存”按钮直接调用一次接口
        requireAdmin(authentication);
        return sysConfigService.upsert(request);
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
