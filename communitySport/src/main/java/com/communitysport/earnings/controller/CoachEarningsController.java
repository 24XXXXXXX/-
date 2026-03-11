package com.communitysport.earnings.controller;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.earnings.dto.CoachEarningPageResponse;
import com.communitysport.earnings.dto.CoachEarningsStatsResponse;
import com.communitysport.earnings.service.CoachEarningsService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CoachEarningsController {

    private final CoachEarningsService coachEarningsService;

    public CoachEarningsController(CoachEarningsService coachEarningsService) {
        this.coachEarningsService = coachEarningsService;
    }

    @GetMapping("/api/coach/earnings/stats")
    public CoachEarningsStatsResponse stats(Authentication authentication) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        return coachEarningsService.stats(au);
    }

    @GetMapping("/api/coach/earnings")
    public CoachEarningPageResponse list(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        return coachEarningsService.list(au, page, size);
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
                if (StringUtils.hasText(auth) && ("ROLE_COACH".equals(auth) || "ROLE_ADMIN".equals(auth))) {
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
