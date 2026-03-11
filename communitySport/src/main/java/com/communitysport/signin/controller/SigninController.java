package com.communitysport.signin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.signin.dto.SigninResponse;
import com.communitysport.signin.dto.SigninStatusResponse;
import com.communitysport.signin.service.SigninService;

@RestController
public class SigninController {

    private final SigninService signinService;

    public SigninController(SigninService signinService) {
        this.signinService = signinService;
    }

    @PostMapping("/api/signin")
    public SigninResponse signin(Authentication authentication) {
        return signinService.signin(getPrincipal(authentication));
    }

    @GetMapping("/api/signin/status")
    public SigninStatusResponse status(Authentication authentication) {
        return signinService.status(getPrincipal(authentication));
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
