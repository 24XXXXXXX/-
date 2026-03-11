package com.communitysport.withdraw.controller;

import java.util.Collection;

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

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.withdraw.dto.WithdrawCreateRequest;
import com.communitysport.withdraw.dto.WithdrawItem;
import com.communitysport.withdraw.dto.WithdrawPageResponse;
import com.communitysport.withdraw.service.CoachWithdrawService;

@RestController
public class CoachWithdrawController {

    private final CoachWithdrawService coachWithdrawService;

    public CoachWithdrawController(CoachWithdrawService coachWithdrawService) {
        this.coachWithdrawService = coachWithdrawService;
    }

    @PostMapping("/api/coach/withdraw-requests")
    public WithdrawItem create(Authentication authentication, @RequestBody WithdrawCreateRequest request) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        return coachWithdrawService.create(au, request);
    }

    @GetMapping("/api/coach/withdraw-requests")
    public WithdrawPageResponse myRequests(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        AuthenticatedUser au = getPrincipal(authentication);
        requireCoach(authentication);
        return coachWithdrawService.myRequests(au, page, size, status);
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
