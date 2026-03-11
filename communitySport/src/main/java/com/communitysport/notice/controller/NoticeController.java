package com.communitysport.notice.controller;

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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.notice.dto.NoticeDetailResponse;
import com.communitysport.notice.dto.NoticePageResponse;
import com.communitysport.notice.dto.NoticePublishedRequest;
import com.communitysport.notice.dto.NoticeUpsertRequest;
import com.communitysport.notice.service.NoticeService;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.upload.dto.FileUploadResponse;

@RestController
public class NoticeController {

    // 公告/通知 Controller：
    // - 公共端：/api/notices，提供公告列表/详情（用户可见内容）
    // - 管理端：/api/admin/notices，提供公告后台管理（新增/编辑/发布/封面上传/删除）
    //
    // 权限：
    // - 管理端接口仅允许 ROLE_ADMIN
    // - Controller 内显式 requireAdmin，属于防御式校验

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/api/notices")
    public NoticePageResponse list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "noticeType", required = false) String noticeType,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        // 公共公告列表：通常只返回已发布（published=true）的记录
        return noticeService.listPublic(page, size, noticeType, keyword);
    }

    @GetMapping("/api/notices/{id}")
    public NoticeDetailResponse detail(@PathVariable("id") Long id) {
        // 公共公告详情：未发布/不存在的访问行为如何处理由 Service 决定
        return noticeService.publicDetail(id);
    }

    @GetMapping("/api/admin/notices")
    public NoticePageResponse adminList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "published", required = false) Integer published,
            @RequestParam(name = "noticeType", required = false) String noticeType,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        // 管理端公告列表：支持按 published、类型、关键字筛选
        requireAdmin(authentication);
        return noticeService.adminList(page, size, published, noticeType, keyword);
    }

    @GetMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse adminDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 管理端公告详情：可查看未发布草稿
        requireAdmin(authentication);
        return noticeService.adminDetail(id);
    }

    @PostMapping("/api/admin/notices")
    public NoticeDetailResponse create(Authentication authentication, @RequestBody NoticeUpsertRequest request) {
        // 新建公告：运营配置变更，显式取 principal 用于记录操作人（审计）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return noticeService.create(au, request);
    }

    @PutMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse update(Authentication authentication, @PathVariable("id") Long id, @RequestBody NoticeUpsertRequest request) {
        // 编辑公告：同样记录操作人（审计）
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return noticeService.update(au, id, request);
    }

    @PostMapping("/api/admin/notices/{id}/published")
    public NoticeDetailResponse updatePublished(Authentication authentication, @PathVariable("id") Long id, @RequestBody NoticePublishedRequest request) {
        // 发布/撤回：单独接口便于后台做“发布开关”
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return noticeService.updatePublished(au, id, request);
    }

    @PostMapping("/api/admin/notices/{id}/cover")
    public FileUploadResponse uploadCover(Authentication authentication, @PathVariable("id") Long id, @RequestParam("file") MultipartFile file) {
        // 上传封面图：
        // - 返回 fileName + url
        // - Service 内通常会完成上传并把 url 写回 notice 表
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        String url = noticeService.uploadAndUpdateCover(au, id, file);
        FileUploadResponse resp = new FileUploadResponse();
        resp.setFileName(file == null ? null : file.getOriginalFilename());
        resp.setUrl(url);
        return resp;
    }

    @DeleteMapping("/api/admin/notices/{id}")
    public void delete(Authentication authentication, @PathVariable("id") Long id) {
        // 删除公告：危险操作，需管理员权限 + 审计
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        noticeService.delete(au, id);
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
