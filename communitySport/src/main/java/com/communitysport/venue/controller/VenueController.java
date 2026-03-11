package com.communitysport.venue.controller;

import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.venue.dto.VenueCreateRequest;
import com.communitysport.venue.dto.VenueDetailResponse;
import com.communitysport.venue.dto.VenuePageResponse;
import com.communitysport.venue.dto.VenueStatusRequest;
import com.communitysport.venue.dto.VenueTypeItem;
import com.communitysport.venue.service.VenueService;

/**
 * 场地接口（Venue）。
 *
 * <p>这组接口覆盖两类访问者：
 * <p>- 公众/用户端：浏览场地类型、场地列表、场地详情
 * <p>- 员工/管理员端：新增/编辑场地、修改状态、上传场地图片
 *
 * <p>权限设计：
 * <p>- GET /api/venues/** 基本是公开可访问（详情接口对非 staff 会增加 clickCount）
 * <p>- 写操作（POST/PATCH/上传）需要 STAFF 或 ADMIN
 */
@RestController
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping("/api/venue/types")
    public List<VenueTypeItem> types() {
        // 场地类型：用于前端筛选（例如篮球场/羽毛球场/游泳馆...）。
        return venueService.listTypes();
    }

    @GetMapping("/api/venues")
    public VenuePageResponse list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "typeId", required = false) Long typeId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status
    ) {
        // 场地列表：支持分页 + 类型筛选 + 关键词搜索 + 状态筛选。
        //
        // 注意：status 参数如果由前台传入，理论上可能被用来“查看非 ACTIVE 场地”。
        // 实际产品中可以考虑：公众端强制只返回 ACTIVE；管理员端才允许传 status。
        return venueService.listVenues(page, size, typeId, keyword, status);
    }

    @GetMapping("/api/venues/{id}")
    public VenueDetailResponse detail(Authentication authentication, @PathVariable("id") Long id) {
        // 场地详情：对 staff 与普通用户做了不同处理。
        //
        // - staff（STAFF/ADMIN）：直接查询，不增加点击量（避免后台查看刷点击）
        // - 公众/用户端：查询 + clickCount 自增（用于热门排序/运营统计）
        if (isStaff(authentication)) {
            return venueService.getVenue(id);
        }
        return venueService.publicGetVenueAndIncreaseClick(id);
    }

    @PostMapping("/api/venues")
    public VenueDetailResponse create(Authentication authentication, @RequestBody VenueCreateRequest request) {
        // 新增场地：仅 STAFF/ADMIN
        requireStaff(authentication);
        return venueService.createVenue(request);
    }

    @PatchMapping("/api/venues/{id}")
    public VenueDetailResponse update(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody VenueCreateRequest request
    ) {
        // 编辑场地：仅 STAFF/ADMIN
        requireStaff(authentication);
        return venueService.updateVenue(id, request);
    }

    @PatchMapping("/api/venues/{id}/status")
    public VenueDetailResponse updateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody VenueStatusRequest request
    ) {
        // 修改场地状态：ACTIVE/MAINTENANCE/DISABLED
        requireStaff(authentication);
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }
        return venueService.updateStatus(id, request.getStatus());
    }

    @PostMapping("/api/venues/{id}/photos")
    public List<String> uploadVenuePhotos(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestParam("files") MultipartFile[] files
    ) {
        // 上传场地图片：上传后会把 URL 列表序列化为 JSON 存入 venue.cover_url。
        requireStaff(authentication);
        return venueService.uploadVenuePhotos(id, files);
    }

    private void requireStaff(Authentication authentication) {
        // 业务侧显式校验 STAFF/ADMIN。
        // 与 SecurityConfig 的路由鉴权形成“双保险”。
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
                if ("ROLE_ADMIN".equals(auth) || "ROLE_STAFF".equals(auth)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private boolean isStaff(Authentication authentication) {
        // 判断当前登录用户是否具备后台身份（STAFF/ADMIN）。
        // 该方法用于“同一个接口对不同身份做不同逻辑”。
        if (authentication == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null) {
            return false;
        }
        for (GrantedAuthority a : authorities) {
            if (a == null) {
                continue;
            }
            String auth = a.getAuthority();
            if ("ROLE_ADMIN".equals(auth) || "ROLE_STAFF".equals(auth)) {
                return true;
            }
        }
        return false;
    }
}
