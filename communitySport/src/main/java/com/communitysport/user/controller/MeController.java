package com.communitysport.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.user.dto.ChangePasswordRequest;
import com.communitysport.user.dto.MeResponse;
import com.communitysport.user.dto.MeUpdateRequest;
import com.communitysport.user.dto.UserAddressCreateRequest;
import com.communitysport.user.dto.UserAddressItem;
import com.communitysport.user.dto.UserAddressUpdateRequest;
import com.communitysport.user.service.MeService;
import com.communitysport.upload.dto.FileUploadResponse;

@RestController
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/api/me")
    public MeResponse me(Authentication authentication) {
        return meService.getMe(requireAu(authentication));
    }

    @PutMapping("/api/me")
    public MeResponse updateMe(Authentication authentication, @RequestBody MeUpdateRequest request) {
        return meService.updateMe(requireAu(authentication), request);
    }

    @PostMapping("/api/me/avatar")
    public FileUploadResponse uploadAvatar(Authentication authentication, @RequestParam("file") MultipartFile file) {
        String url = meService.uploadAvatar(requireAu(authentication), file);
        FileUploadResponse resp = new FileUploadResponse();
        resp.setFileName(file == null ? null : file.getOriginalFilename());
        resp.setUrl(url);
        return resp;
    }

    @PostMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @RequestBody ChangePasswordRequest request) {
        meService.changePassword(requireAu(authentication), request);
    }

    @GetMapping("/api/me/addresses")
    public List<UserAddressItem> myAddresses(Authentication authentication) {
        return meService.myAddresses(requireAu(authentication));
    }

    @PostMapping("/api/me/addresses")
    public UserAddressItem createAddress(Authentication authentication, @RequestBody UserAddressCreateRequest request) {
        return meService.createAddress(requireAu(authentication), request);
    }

    @PutMapping("/api/me/addresses/{id}")
    public UserAddressItem updateAddress(Authentication authentication, @PathVariable("id") Long id, @RequestBody UserAddressUpdateRequest request) {
        return meService.updateAddress(requireAu(authentication), id, request);
    }

    @DeleteMapping("/api/me/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(Authentication authentication, @PathVariable("id") Long id) {
        meService.deleteAddress(requireAu(authentication), id);
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
