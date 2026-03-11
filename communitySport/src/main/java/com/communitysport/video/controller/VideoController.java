package com.communitysport.video.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.video.dto.CoachVideoDetailResponse;
import com.communitysport.video.dto.CoachVideoPageResponse;
import com.communitysport.video.dto.VideoPurchasePageResponse;
import com.communitysport.video.dto.VideoPurchaseResponse;
import com.communitysport.video.service.CoachVideoService;

@RestController
public class VideoController {

    // 面向“用户端”的公开视频接口：
    // - /api/videos：公开目录（可匿名浏览），登录后会额外返回 purchased 等用户态信息
    // - /api/videos/{id}：公开视频详情（可匿名访问，但付费视频是否能拿到 videoUrl 由服务端控制）
    // - /api/videos/{id}/purchase：购买/解锁（必须登录）
    // - /api/video-purchases：我的购买记录（必须登录）
    //
    // Controller 职责：
    // - 只负责从 Spring Security 拿到 principal（AuthenticatedUser）
    // - 参数透传给 Service，并把认证边界（可匿名/必须登录）表达清楚

    private final CoachVideoService coachVideoService;

    public VideoController(CoachVideoService coachVideoService) {
        this.coachVideoService = coachVideoService;
    }

    @GetMapping("/api/videos")
    public CoachVideoPageResponse list(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "coachUserId", required = false) Long coachUserId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        // 允许匿名访问：未登录时 principal 为空，Service 会走“公共视图”逻辑（不带用户态 purchased）
        AuthenticatedUser principal = getPrincipalNullable(authentication);
        return coachVideoService.listPublic(principal, page, size, coachUserId, category, keyword);
    }

    @GetMapping("/api/videos/{id:\\d+}")
    public CoachVideoDetailResponse detail(Authentication authentication, @PathVariable("id") Long id) {
        // 允许匿名访问：是否 purchased / 是否返回 videoUrl 由 Service 基于登录态与购买态决定
        AuthenticatedUser principal = getPrincipalNullable(authentication);
        return coachVideoService.publicDetail(principal, id);
    }

    @GetMapping("/api/videos/categories")
    public List<String> categories() {
        return coachVideoService.listDistinctPublicCategories();
    }

    @PostMapping("/api/videos/{id}/purchase")
    public VideoPurchaseResponse purchase(Authentication authentication, @PathVariable("id") Long id) {
        // 购买必须登录：这里显式要求 principal 存在，不接受匿名
        AuthenticatedUser principal = getPrincipalRequired(authentication);
        return coachVideoService.purchase(principal, id);
    }

    @GetMapping("/api/video-purchases")
    public VideoPurchasePageResponse myPurchases(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        // “我的购买记录”必须登录
        AuthenticatedUser principal = getPrincipalRequired(authentication);
        return coachVideoService.myPurchases(principal, page, size);
    }

    private AuthenticatedUser getPrincipalRequired(Authentication authentication) {
        // required：认证信息缺失或 principal 类型不对 => 401
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return au;
    }

    private AuthenticatedUser getPrincipalNullable(Authentication authentication) {
        // nullable：未登录时直接返回 null，让 Service 走匿名视角
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            return null;
        }
        return au;
    }
}
