package com.communitysport.venue.controller;

import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.venue.dto.VenueTimeslotGenerateRequest;
import com.communitysport.venue.dto.VenueTimeslotGenerateResponse;
import com.communitysport.venue.dto.VenueTimeslotItem;
import com.communitysport.venue.dto.VenueTimeslotStatusRequest;
import com.communitysport.venue.service.VenueTimeslotService;

/**
 * 场地可预约时段（Timeslot）接口。
 *
 * <p>用户端核心能力：
 * <p>- 按日期查看某个场地的可预约时段列表（含价格/状态）
 *
 * <p>后台维护能力（STAFF/ADMIN）：
 * <p>- 批量生成某天的时段（按 startHour/endHour/slotMinutes 切分）
 * <p>- 手动把时段置为 AVAILABLE/BLOCKED（用于临时维护、封场等）
 */
@RestController
public class VenueTimeslotController {

    private final VenueTimeslotService venueTimeslotService;

    public VenueTimeslotController(VenueTimeslotService venueTimeslotService) {
        this.venueTimeslotService = venueTimeslotService;
    }

    @GetMapping("/api/venues/{venueId}/timeslots")
    public List<VenueTimeslotItem> list(
            @PathVariable("venueId") Long venueId,
            @RequestParam("date") LocalDate date,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "startTime", required = false) LocalTime startTime,
            @RequestParam(name = "endTime", required = false) LocalTime endTime,
            @RequestParam(name = "minPrice", required = false) Integer minPrice,
            @RequestParam(name = "maxPrice", required = false) Integer maxPrice
    ) {
        // 用户端查询某天时段：
        // - 通过 status 可以筛出 AVAILABLE/BOOKED/BLOCKED
        // - 通过 startTime/endTime/minPrice/maxPrice 做二次筛选
        return venueTimeslotService.listByDate(venueId, date, status, startTime, endTime, minPrice, maxPrice);
    }

    @PostMapping("/api/venues/{venueId}/timeslots/generate")
    public VenueTimeslotGenerateResponse generate(
            Authentication authentication,
            @PathVariable("venueId") Long venueId,
            @RequestBody VenueTimeslotGenerateRequest request
    ) {
        // 后台生成时段：避免人工一条条录入。
        requireStaff(authentication);
        return venueTimeslotService.generate(venueId, request);
    }

    @PatchMapping("/api/timeslots/{id}/status")
    public VenueTimeslotItem updateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody VenueTimeslotStatusRequest request
    ) {
        // 后台手动改时段状态：
        // - AVAILABLE：可预约
        // - BLOCKED：不可预约
        // 已 BOOKED 的时段不允许改（避免破坏订单一致性）。
        requireStaff(authentication);
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }
        return venueTimeslotService.updateStatus(id, request.getStatus());
    }

    private void requireStaff(Authentication authentication) {
        // 显式校验 STAFF/ADMIN（与路由鉴权形成双保险）。
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
