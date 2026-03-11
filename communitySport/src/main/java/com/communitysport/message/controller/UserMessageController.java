package com.communitysport.message.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.message.dto.UnreadCountResponse;
import com.communitysport.message.dto.UserMessagePageResponse;
import com.communitysport.message.service.UserMessageService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class UserMessageController {

    private final UserMessageService userMessageService;

    public UserMessageController(UserMessageService userMessageService) {
        this.userMessageService = userMessageService;
    }

    @GetMapping("/api/me/messages")
    public UserMessagePageResponse myMessages(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "readFlag", required = false) Integer readFlag
    ) {
        return userMessageService.myMessages(requireAu(authentication), page, size, readFlag);
    }

    @GetMapping("/api/me/messages/unread-count")
    public UnreadCountResponse unreadCount(Authentication authentication) {
        return userMessageService.unreadCount(requireAu(authentication));
    }

    @PostMapping("/api/me/messages/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication authentication, @PathVariable("id") Long id) {
        userMessageService.markRead(requireAu(authentication), id);
    }

    @PostMapping("/api/me/messages/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication authentication) {
        userMessageService.markAllRead(requireAu(authentication));
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
