package com.communitysport.home.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.home.dto.HomeRecommendationsResponse;
import com.communitysport.home.service.HomeRecommendationService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class HomeRecommendationController {

    private final HomeRecommendationService homeRecommendationService;

    public HomeRecommendationController(HomeRecommendationService homeRecommendationService) {
        this.homeRecommendationService = homeRecommendationService;
    }

    @GetMapping("/api/home/recommendations")
    public HomeRecommendationsResponse recommendations(
            Authentication authentication,
            @RequestParam(name = "bannerSize", required = false) Integer bannerSize,
            @RequestParam(name = "venueSize", required = false) Integer venueSize,
            @RequestParam(name = "courseSize", required = false) Integer courseSize,
            @RequestParam(name = "equipmentSize", required = false) Integer equipmentSize,
            @RequestParam(name = "noticeSize", required = false) Integer noticeSize
    ) {
        AuthenticatedUser principal = getPrincipalNullable(authentication);
        return homeRecommendationService.recommendations(principal, bannerSize, venueSize, courseSize, equipmentSize, noticeSize);
    }

    private AuthenticatedUser getPrincipalNullable(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            return null;
        }
        return au;
    }

    @SuppressWarnings("unused")
    private AuthenticatedUser getPrincipalRequired(Authentication authentication) {
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
