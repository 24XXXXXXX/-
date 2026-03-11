package com.communitysport.coursereview.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.coursereview.dto.CourseReviewCreateRequest;
import com.communitysport.coursereview.dto.CourseReviewListItem;
import com.communitysport.coursereview.dto.CourseReviewPageResponse;
import com.communitysport.coursereview.service.CourseReviewService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CourseReviewController {

    private final CourseReviewService courseReviewService;

    public CourseReviewController(CourseReviewService courseReviewService) {
        this.courseReviewService = courseReviewService;
    }

    @PostMapping("/api/course-reviews")
    public CourseReviewListItem create(Authentication authentication, @RequestBody CourseReviewCreateRequest request) {
        return courseReviewService.create(getPrincipal(authentication), request);
    }

    @GetMapping("/api/courses/{courseId}/reviews")
    public CourseReviewPageResponse listByCourse(
            @PathVariable("courseId") Long courseId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return courseReviewService.listByCourse(courseId, page, size);
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
}
