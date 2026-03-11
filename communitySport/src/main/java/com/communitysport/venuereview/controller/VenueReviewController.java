package com.communitysport.venuereview.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.venuereview.dto.VenueReviewCreateRequest;
import com.communitysport.venuereview.dto.VenueReviewListItem;
import com.communitysport.venuereview.dto.VenueReviewPageResponse;
import com.communitysport.venuereview.service.VenueReviewService;

@RestController
public class VenueReviewController {

    private final VenueReviewService venueReviewService;

    public VenueReviewController(VenueReviewService venueReviewService) {
        this.venueReviewService = venueReviewService;
    }

    @PostMapping("/api/venue-reviews")
    public VenueReviewListItem create(Authentication authentication, @RequestBody VenueReviewCreateRequest request) {
        return venueReviewService.create(getPrincipal(authentication), request);
    }

    @GetMapping("/api/venues/{venueId}/reviews")
    public VenueReviewPageResponse listByVenue(
            @PathVariable("venueId") Long venueId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return venueReviewService.listByVenue(venueId, page, size);
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
