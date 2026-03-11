package com.communitysport.consultation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.consultation.dto.ConsultationCreateRequest;
import com.communitysport.consultation.dto.ConsultationDetailResponse;
import com.communitysport.consultation.dto.ConsultationMessageItem;
import com.communitysport.consultation.dto.ConsultationPageResponse;
import com.communitysport.consultation.dto.ConsultationReplyRequest;
import com.communitysport.consultation.service.CourseConsultationService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CourseConsultationController {

    private final CourseConsultationService courseConsultationService;

    public CourseConsultationController(CourseConsultationService courseConsultationService) {
        this.courseConsultationService = courseConsultationService;
    }

    @PostMapping("/api/course-consultations")
    public ConsultationDetailResponse create(Authentication authentication, @RequestBody ConsultationCreateRequest request) {
        return courseConsultationService.create(requireAu(authentication), request);
    }

    @GetMapping("/api/course-consultations")
    public ConsultationPageResponse myList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        return courseConsultationService.myConsultations(requireAu(authentication), page, size, status);
    }

    @GetMapping("/api/course-consultations/{id}")
    public ConsultationDetailResponse myDetail(Authentication authentication, @PathVariable("id") Long id) {
        return courseConsultationService.myDetail(requireAu(authentication), id);
    }

    @PostMapping("/api/course-consultations/{id}/messages")
    public ConsultationMessageItem addMessage(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody ConsultationReplyRequest request
    ) {
        return courseConsultationService.userAddMessage(requireAu(authentication), id, request);
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
}
