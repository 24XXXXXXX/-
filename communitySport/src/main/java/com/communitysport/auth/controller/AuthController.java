package com.communitysport.auth.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.communitysport.auth.dto.PasswordResetConfirmRequest;
import com.communitysport.auth.dto.LoginRequest;
import com.communitysport.auth.dto.LogoutRequest;
import com.communitysport.auth.dto.PasswordResetSendCodeRequest;
import com.communitysport.auth.dto.RegisterRequest;
import com.communitysport.auth.dto.RefreshRequest;
import com.communitysport.auth.dto.TokenPairResponse;
import com.communitysport.auth.service.AuthService;
import com.communitysport.auth.service.PasswordResetService;

/**
 * 认证相关接口（/api/auth/...）。
 *
 * <p>这个 Controller 的设计原则是：
 * <p>- 尽量“薄”（Thin Controller）：只做参数接收与路由映射，不在这里写复杂业务。
 * <p>- 真实业务都交给 Service（AuthService / PasswordResetService）处理，便于复用与测试。
 *
 * <p>本模块覆盖的核心能力：
 * <p>- 登录：校验账号密码，签发 AccessToken + RefreshToken（双 Token）
 * <p>- 注册：创建账号并赋予默认 USER 角色，同时签发双 Token
 * <p>- 刷新：用 RefreshToken 换新 Token Pair
 * <p>- 退出：吊销 refresh token（让它无法继续刷新）
 * <p>- 找回密码：邮件验证码 + 重置密码
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public TokenPairResponse login(@RequestBody LoginRequest request) {
        // 登录：成功返回 tokenPair + 用户基础信息（userId/roles...）
        return authService.login(request);
    }

    @PostMapping("/register")
    public TokenPairResponse register(@RequestBody RegisterRequest request) {
        // 注册：创建账号 + 默认 USER 角色 + 返回 tokenPair
        return authService.register(request);
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@RequestBody RefreshRequest request) {
        // 刷新：用 refreshToken 换取新的 accessToken/refreshToken
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public void logout(@RequestBody LogoutRequest request) {
        // 退出：服务端吊销 refreshToken（更安全）
        authService.logout(request);
    }

    @PostMapping("/password-reset/code")
    public void sendPasswordResetCode(@RequestBody PasswordResetSendCodeRequest request) {
        // 找回密码第一步：发送邮件验证码（验证码存 Redis，带 TTL + 冷却时间）
        passwordResetService.sendResetCode(request);
    }

    @PostMapping("/password-reset/confirm")
    public void confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        // 找回密码第二步：校验验证码并重置密码，同时吊销该用户所有 refreshToken（防止旧 token 继续刷新）
        passwordResetService.confirmReset(request);
    }
}
