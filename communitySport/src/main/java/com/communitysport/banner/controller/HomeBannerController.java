package com.communitysport.banner.controller;

import java.util.Collection;
import java.util.List;

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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.banner.dto.HomeBannerCreateRequest;
import com.communitysport.banner.dto.HomeBannerEnabledRequest;
import com.communitysport.banner.dto.HomeBannerItem;
import com.communitysport.banner.dto.HomeBannerUpdateRequest;
import com.communitysport.banner.service.HomeBannerService;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.upload.dto.FileUploadResponse;

@RestController
public class HomeBannerController {

    // 首页轮播图 Controller：
    // - 公共端：/api/home-banners，给所有用户首页展示
    // - 管理端：/api/admin/home-banners，提供后台维护（增删改、启用/停用、上传图片）
    //
    // 权限：
    // - 管理端接口仅允许 ROLE_ADMIN
    // - Controller 内显式 requireAdmin，属于防御式校验

    private final HomeBannerService homeBannerService;

    public HomeBannerController(HomeBannerService homeBannerService) {
        this.homeBannerService = homeBannerService;
    }

    @GetMapping("/api/home-banners")
    public List<HomeBannerItem> list() {
        // 公共轮播图列表：通常只返回 enabled=true 的条目（具体由 Service 决定）
        return homeBannerService.listPublic();
    }

    @GetMapping("/api/admin/home-banners")
    public List<HomeBannerItem> adminList(Authentication authentication, @RequestParam(name = "enabled", required = false) Integer enabled) {
        // 管理端列表：可按 enabled 状态筛选
        requireAdmin(authentication);
        return homeBannerService.adminList(enabled);
    }

    @PostMapping("/api/admin/home-banners")
    public HomeBannerItem create(Authentication authentication, @RequestBody HomeBannerCreateRequest request) {
        // 创建轮播图属于运营配置变更，通常需要记录操作人（审计）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return homeBannerService.create(au, request);
    }

    @PutMapping("/api/admin/home-banners/{id}")
    public HomeBannerItem update(Authentication authentication, @PathVariable("id") Long id, @RequestBody HomeBannerUpdateRequest request) {
        // 更新轮播图内容（标题/跳转链接等，具体字段见 DTO 与 Service）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return homeBannerService.update(au, id, request);
    }

    @PostMapping("/api/admin/home-banners/{id}/enabled")
    public HomeBannerItem updateEnabled(Authentication authentication, @PathVariable("id") Long id, @RequestBody HomeBannerEnabledRequest request) {
        // 启用/停用：单独接口便于前端做开关按钮
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return homeBannerService.updateEnabled(au, id, request);
    }

    @PostMapping("/api/admin/home-banners/{id}/image")
    public FileUploadResponse uploadImage(Authentication authentication, @PathVariable("id") Long id, @RequestParam("file") MultipartFile file) {
        // 上传轮播图图片：
        // - 返回 FileUploadResponse（fileName + url），便于前端直接预览/回填
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        String url = homeBannerService.uploadAndUpdateImage(au, id, file);
        FileUploadResponse resp = new FileUploadResponse();
        resp.setFileName(file == null ? null : file.getOriginalFilename());
        resp.setUrl(url);
        return resp;
    }

    @DeleteMapping("/api/admin/home-banners/{id}")
    public void delete(Authentication authentication, @PathVariable("id") Long id) {
        // 删除轮播图：通常是运营配置清理操作
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        homeBannerService.delete(au, id);
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
