package com.communitysport.favorite.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.favorite.dto.FavoriteCountResponse;
import com.communitysport.favorite.dto.FavoritePageResponse;
import com.communitysport.favorite.dto.FavoriteStatusResponse;
import com.communitysport.favorite.dto.FavoriteToggleRequest;
import com.communitysport.favorite.service.FavoriteService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/api/favorites")
    public FavoriteStatusResponse favorite(Authentication authentication, @RequestBody FavoriteToggleRequest request) {
        return favoriteService.favorite(getPrincipal(authentication), request);
    }

    @DeleteMapping("/api/favorites")
    public FavoriteStatusResponse unfavorite(
            Authentication authentication,
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId
    ) {
        return favoriteService.unfavorite(getPrincipal(authentication), targetType, targetId);
    }

    @GetMapping("/api/favorites/status")
    public FavoriteStatusResponse status(
            Authentication authentication,
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId
    ) {
        return favoriteService.status(getPrincipal(authentication), targetType, targetId);
    }

    @GetMapping("/api/favorites")
    public FavoritePageResponse myFavorites(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "targetType", required = false) String targetType
    ) {
        return favoriteService.myFavorites(getPrincipal(authentication), page, size, targetType);
    }

    @GetMapping("/api/favorites/count")
    public FavoriteCountResponse count(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId
    ) {
        return favoriteService.count(targetType, targetId);
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
