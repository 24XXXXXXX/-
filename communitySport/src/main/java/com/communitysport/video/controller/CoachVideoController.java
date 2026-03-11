package com.communitysport.video.controller;

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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.upload.dto.FileUploadResponse;
import com.communitysport.video.dto.CoachVideoCreateRequest;
import com.communitysport.video.dto.CoachVideoDetailResponse;
import com.communitysport.video.dto.CoachVideoPageResponse;
import com.communitysport.video.dto.CoachVideoStatusRequest;
import com.communitysport.video.dto.CoachVideoUpdateRequest;
import com.communitysport.video.dto.CoachVideoUploadResponse;
import com.communitysport.video.service.CoachVideoService;

@RestController
public class CoachVideoController {

    // 面向“教练端”的视频管理接口：
    // - 上传视频文件（生成可访问的视频 URL）
    // - 新建/编辑/上架下架视频元数据
    // - 查询“我的视频列表/详情”
    //
    // 安全边界：
    // - 所有接口都要求登录
    // - 且必须具备 ROLE_COACH（教练身份）
    //
    // 这里选择在 Controller 层做 requireCoach：
    // - 让权限不足时尽早返回 403，避免进入 Service 做无谓的业务查询
    // - 也便于把“接口面向谁”表达得更清晰

    private final CoachVideoService coachVideoService;

    public CoachVideoController(CoachVideoService coachVideoService) {
        this.coachVideoService = coachVideoService;
    }

    @PostMapping("/api/coach/videos/upload")
    public CoachVideoUploadResponse upload(Authentication authentication, @RequestParam("file") MultipartFile file) {
        AuthenticatedUser principal = getPrincipal(authentication);
        // 角色校验：必须是教练
        requireCoach(authentication);
        return coachVideoService.uploadVideo(principal, file);
    }

    @PostMapping("/api/coach/videos/{id}/cover")
    public FileUploadResponse uploadCover(Authentication authentication, @PathVariable("id") Long id, @RequestParam("file") MultipartFile file) {
        AuthenticatedUser principal = getPrincipal(authentication);
        // 角色校验：必须是教练
        requireCoach(authentication);
        String url = coachVideoService.uploadAndUpdateCover(principal, id, file);
        FileUploadResponse resp = new FileUploadResponse();
        resp.setFileName(file == null ? null : file.getOriginalFilename());
        resp.setUrl(url);
        return resp;
    }

    @PostMapping("/api/coach/videos")
    public CoachVideoDetailResponse create(Authentication authentication, @RequestBody CoachVideoCreateRequest request) {
        AuthenticatedUser principal = getPrincipal(authentication);
        // 角色校验：必须是教练
        requireCoach(authentication);
        return coachVideoService.create(principal, request);
    }

    @PutMapping("/api/coach/videos/{id}")
    public CoachVideoDetailResponse update(Authentication authentication, @PathVariable("id") Long id, @RequestBody CoachVideoUpdateRequest request) {
        AuthenticatedUser principal = getPrincipal(authentication);
        // 角色校验：必须是教练
        requireCoach(authentication);
        return coachVideoService.update(principal, id, request);
    }

    @PostMapping("/api/coach/videos/{id}/status")
    public CoachVideoDetailResponse updateStatus(Authentication authentication, @PathVariable("id") Long id, @RequestBody CoachVideoStatusRequest request) {
        AuthenticatedUser principal = getPrincipal(authentication);
        // 角色校验：必须是教练
        requireCoach(authentication);
        return coachVideoService.updateStatus(principal, id, request);
    }

    @GetMapping("/api/coach/videos")
    public CoachVideoPageResponse myVideos(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        AuthenticatedUser principal = getPrincipal(authentication);
        // 角色校验：必须是教练
        requireCoach(authentication);
        return coachVideoService.myVideos(principal, page, size, status);
    }

    @GetMapping("/api/coach/videos/{id}")
    public CoachVideoDetailResponse myDetail(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser principal = getPrincipal(authentication);
        // 角色校验：必须是教练
        requireCoach(authentication);
        return coachVideoService.myDetail(principal, id);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 教练端接口全部要求登录：认证信息缺失或 principal 类型不对 => 401
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        return au;
    }

    private void requireCoach(Authentication authentication) {
        // 细粒度权限：要求拥有 ROLE_COACH
        // - 401：未登录
        // - 403：已登录但不是教练
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
                if (StringUtils.hasText(auth) && "ROLE_COACH".equals(auth)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要教练认证");
        }
    }
}
