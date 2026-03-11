package com.communitysport.booking.controller;

import java.util.Collection;
import java.util.List;

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

import com.communitysport.booking.dto.BookingCreateRequest;
import com.communitysport.booking.dto.BookingDetailResponse;
import com.communitysport.booking.dto.BookingPageResponse;
import com.communitysport.booking.dto.BookingVerifyRequest;
import com.communitysport.booking.dto.BookingVerifyLogItem;
import com.communitysport.booking.service.VenueBookingService;
import com.communitysport.security.AuthenticatedUser;

/**
 * 场地预约订单接口（venue_booking）。
 *
 * <p>接口按访问者分为三类：
 * <p>- 用户端：查看自己的订单、下单、取消
 * <p>- 后台（STAFF/ADMIN）：查看全部订单、查看核销日志
 * <p>- 核销：工作人员用核销码/订单号进行核销
 */
@RestController
public class VenueBookingController {

    // 场地预约订单 Controller：
    // - 用户端：下单（钱包扣款）、查询我的订单/详情、取消（可能触发退款）
    // - 后台端（STAFF/ADMIN）：全量订单列表/详情、查看核销日志
    // - 核销：由工作人员执行的“线下履约确认”，属于高风险动作，需要严格限制后台身份
    //
    // 权限设计：
    // - /api/admin/** 虽然在路由层面通常已限制权限，但这里仍显式 requireStaff（防御式校验）
    // - Service 内仍会做资源与状态校验（例如订单状态是否允许核销/取消）

    private final VenueBookingService venueBookingService;

    public VenueBookingController(VenueBookingService venueBookingService) {
        this.venueBookingService = venueBookingService;
    }

    @GetMapping("/api/bookings")
    public BookingPageResponse myBookings(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        // 用户端：我的订单列表。
        return venueBookingService.myBookings(getPrincipal(authentication), page, size, status);
    }

    @GetMapping("/api/bookings/{id}")
    public BookingDetailResponse myBookingDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：我的订单详情。
        return venueBookingService.myBookingDetail(getPrincipal(authentication), id);
    }

    @GetMapping("/api/bookings/{id}/verify-logs")
    public List<BookingVerifyLogItem> myVerifyLogs(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：查看自己订单的核销记录（一般用于确认是否已核销）。
        return venueBookingService.myVerifyLogs(getPrincipal(authentication), id);
    }

    @GetMapping("/api/admin/bookings")
    public BookingPageResponse adminBookings(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "bookingNo", required = false) String bookingNo
    ) {
        // 后台：订单列表（可按 userId/status/bookingNo 过滤）。
        // - 这里的“后台”权限是 STAFF/ADMIN，而不是仅 ADMIN：
        //   因为核销/运营人员需要日常查看履约订单。
        requireStaff(authentication);
        return venueBookingService.adminBookings(page, size, userId, status, bookingNo);
    }

    @GetMapping("/api/admin/bookings/{id}")
    public BookingDetailResponse adminBookingDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 后台：订单详情。
        requireStaff(authentication);
        return venueBookingService.adminBookingDetail(id);
    }

    @GetMapping("/api/admin/bookings/{id}/verify-logs")
    public List<BookingVerifyLogItem> adminVerifyLogs(Authentication authentication, @PathVariable("id") Long id) {
        // 后台：查看某订单的核销日志。
        // - 用于审计：谁在什么时间对该订单做过核销
        requireStaff(authentication);
        return venueBookingService.adminVerifyLogs(id);
    }

    @PostMapping("/api/bookings")
    public BookingDetailResponse create(Authentication authentication, @RequestBody BookingCreateRequest request) {
        // 用户端：创建订单并立即钱包扣款。
        return venueBookingService.create(getPrincipal(authentication), request);
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public BookingDetailResponse cancel(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：取消订单（PAID 会触发退款）。
        return venueBookingService.cancel(getPrincipal(authentication), id);
    }

    @PostMapping("/api/bookings/verify")
    public BookingDetailResponse verify(Authentication authentication, @RequestBody BookingVerifyRequest request) {
        // 核销接口：仅 STAFF/ADMIN。
        // 核销动作属于线下履约确认，必须限制后台身份。
        // - 这里会把操作人（au.userId）传给 Service 记录核销日志
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        return venueBookingService.verify(au.userId(), request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 从 Spring Security 上下文中提取登录用户信息。
        // 异常语义：
        // - 未登录/无法解析 principal => 401
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
        // 业务侧显式校验 STAFF/ADMIN。
        // 与 SecurityConfig 的 URL 鉴权构成双保险。
        //
        // 异常语义：
        // - 401：未登录
        // - 403：已登录但无 STAFF/ADMIN 权限
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
}
