package com.communitysport.consultation.controller;

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

import com.communitysport.consultation.dto.ConsultationDetailResponse;
import com.communitysport.consultation.dto.ConsultationMessageItem;
import com.communitysport.consultation.dto.ConsultationPageResponse;
import com.communitysport.consultation.dto.ConsultationReplyRequest;
import com.communitysport.consultation.service.CourseConsultationService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CoachConsultationController {

    private final CourseConsultationService courseConsultationService;

    public CoachConsultationController(CourseConsultationService courseConsultationService) {
        this.courseConsultationService = courseConsultationService;
    }

    @GetMapping("/api/coach/course-consultations")
    public ConsultationPageResponse list(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        AuthenticatedUser au = requireAu(authentication);
        requireCoach(authentication);
        return courseConsultationService.coachConsultations(au, page, size, status);
    }

    @GetMapping("/api/coach/course-consultations/{id}")
    public ConsultationDetailResponse detail(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser au = requireAu(authentication);
        requireCoach(authentication);
        return courseConsultationService.coachDetail(au, id);
    }

    @PostMapping("/api/coach/course-consultations/{id}/reply")
    public ConsultationMessageItem reply(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ConsultationReplyRequest request
    ) {
        AuthenticatedUser au = requireAu(authentication);
        requireCoach(authentication);
        return courseConsultationService.coachReply(au, id, request);
    }

    @PostMapping("/api/coach/course-consultations/{id}/close")
    public ConsultationDetailResponse close(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser au = requireAu(authentication);
        requireCoach(authentication);
        return courseConsultationService.coachClose(au, id);
    }

    private AuthenticatedUser requireAu(Authentication authentication) {
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
