package com.communitysport.coursebooking.controller;

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

import com.communitysport.coursebooking.dto.CourseBookingCreateRequest;
import com.communitysport.coursebooking.dto.CourseBookingDecisionRequest;
import com.communitysport.coursebooking.dto.CourseBookingDetailResponse;
import com.communitysport.coursebooking.dto.CourseBookingPageResponse;
import com.communitysport.coursebooking.dto.CourseBookingVerifyRequest;
import com.communitysport.coursebooking.service.CourseBookingService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CourseBookingController {

    private final CourseBookingService courseBookingService;

    public CourseBookingController(CourseBookingService courseBookingService) {
        this.courseBookingService = courseBookingService;
    }

    @PostMapping("/api/course-bookings")
    public CourseBookingDetailResponse create(Authentication authentication, @RequestBody CourseBookingCreateRequest request) {
        // 用户端：发起课程预约（占座 + 创建预约单）。
        //
        // 这一步通常不是“支付”，而是先生成预约单，让教练先确认/接单：
        // - PENDING_COACH：等待教练处理
        // - ACCEPTED：教练已接单，用户才可以支付
        // - REJECTED：教练拒单，系统释放名额
        return courseBookingService.create(getPrincipal(authentication), request);
    }

    @PostMapping("/api/course-bookings/{id}/pay")
    public CourseBookingDetailResponse pay(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：支付课程预约。
        //
        // 关键约束：
        // - 只有在教练 ACCEPTED 之后才允许支付
        // - 资金走“钱包”扣款（WalletService.debit），并在同一事务内更新订单为 PAID
        return courseBookingService.pay(getPrincipal(authentication), id);
    }

    @PostMapping("/api/course-bookings/{id}/cancel")
    public CourseBookingDetailResponse cancel(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：取消预约。
        //
        // 幂等性：
        // - 若订单已是 CANCELED/REFUNDED，则直接返回详情（重复点击取消不会报错）
        //
        // 资金规则（由 service 决定）：
        // - 已支付（PAID）通常走退款（钱包 credit）并改成 REFUNDED
        // - 未支付则直接 CANCELED
        return courseBookingService.cancel(getPrincipal(authentication), id);
    }

    @GetMapping("/api/course-bookings")
    public CourseBookingPageResponse myBookings(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        // 用户端：我的课程预约列表（分页）。
        //
        // status 支持：
        // - 单值：PAID
        // - 或逗号分隔多值：PAID,USED（service 里会 normalize）
        return courseBookingService.myBookings(getPrincipal(authentication), page, size, status);
    }

    @GetMapping("/api/course-bookings/{id}")
    public CourseBookingDetailResponse myDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：我的课程预约详情。
        //
        // 安全性：
        // - service 会校验 booking.user_id == 当前用户，避免越权查看他人订单
        return courseBookingService.myBookingDetail(getPrincipal(authentication), id);
    }

    @GetMapping("/api/coach/course-bookings")
    public CourseBookingPageResponse coachBookings(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "bookingNo", required = false) String bookingNo
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：查看“我名下的课程预约单”。
        //
        // 说明：
        // - 教练只能看到属于自己课程的预约（service 内通过 join 校验归属）
        // - 支持按订单状态/订单号筛选，用于教练处理待接单/待核销
        return courseBookingService.coachBookings(au, page, size, status, bookingNo);
    }

    @PostMapping("/api/coach/course-bookings/{id}/accept")
    public CourseBookingDetailResponse accept(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：接单（PENDING_COACH -> ACCEPTED）。
        //
        // 接单后，用户才允许支付（pay 接口会校验必须是 ACCEPTED）
        return courseBookingService.coachAccept(au, id);
    }

    @PostMapping("/api/coach/course-bookings/{id}/reject")
    public CourseBookingDetailResponse reject(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody(required = false) CourseBookingDecisionRequest request
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：拒单（PENDING_COACH -> REJECTED）。
        //
        // 业务后果：
        // - 释放课次名额（service 调用 releaseSeat）
        // - 可记录拒单原因 rejectReason 供用户查看
        return courseBookingService.coachReject(au, id, request);
    }

    @PostMapping("/api/coach/course-bookings/verify")
    public CourseBookingDetailResponse verify(Authentication authentication, @RequestBody CourseBookingVerifyRequest request) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：核销课程。
        //
        // 核销的本质：
        // - 将已支付（PAID）的预约单变为 USED，表示服务已交付
        // - 并在核销成功后给教练入账（钱包 credit，txnType=COACH_COURSE_EARNING）
        //
        // request 支持两种定位方式：
        // - bookingNo（唯一订单号）
        // - verificationCode（6位核销码；可能出现重复，因此需要做“歧义处理”）
        return courseBookingService.coachVerify(au, request);
    }

    @PostMapping("/api/course-bookings/verify")
    public CourseBookingDetailResponse staffVerify(Authentication authentication, @RequestBody CourseBookingVerifyRequest request) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        // 工作人员端：代核销。
        //
        // 为什么需要 staff 核销？
        // - 场馆前台/管理员可能协助核销，或教练不在场时由工作人员执行
        //
        // 安全性：
        // - controller 层 requireStaff 是“角色兜底”
        // - service 里仍会校验订单状态与上课时间等规则
        return courseBookingService.staffVerify(au, request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
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

    private void requireStaff(Authentication authentication) {
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
}
