package com.communitysport.equipmentreview.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.equipmentreview.dto.EquipmentReviewCreateRequest;
import com.communitysport.equipmentreview.dto.EquipmentReviewListItem;
import com.communitysport.equipmentreview.dto.EquipmentReviewPageResponse;
import com.communitysport.equipmentreview.service.EquipmentReviewService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class EquipmentReviewController {

    private final EquipmentReviewService equipmentReviewService;

    public EquipmentReviewController(EquipmentReviewService equipmentReviewService) {
        this.equipmentReviewService = equipmentReviewService;
    }

    @PostMapping("/api/equipment-reviews")
    public EquipmentReviewListItem create(Authentication authentication, @RequestBody EquipmentReviewCreateRequest request) {
        return equipmentReviewService.create(getPrincipal(authentication), request);
    }

    @GetMapping("/api/equipments/{equipmentId}/reviews")
    public EquipmentReviewPageResponse list(
            @PathVariable("equipmentId") Long equipmentId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return equipmentReviewService.listByEquipment(equipmentId, page, size);
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
