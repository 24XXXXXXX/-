package com.communitysport.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.communitysport.auth.dto.PasswordResetConfirmRequest;
import com.communitysport.auth.dto.PasswordResetSendCodeRequest;
import com.communitysport.auth.entity.AuthRefreshToken;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.AuthRefreshTokenMapper;
import com.communitysport.auth.mapper.SysUserMapper;

/**
 * 找回密码服务（邮箱验证码）。
 *
 * <p>整体流程分两步：
 * <p>1）发送验证码：/api/auth/password-reset/code
 * <p>2）确认重置：/api/auth/password-reset/confirm
 *
 * <p>为什么验证码要放 Redis？
 * <p>- 验证码天然有“短有效期（TTL）”，Redis 非常适合做这种临时数据
 * <p>- 不需要落库，避免垃圾数据膨胀
 * <p>- 支持 setIfAbsent 做发送冷却（防刷）
 *
 * <p>安全关键点：
 * <p>- 验证码有效期很短（本项目 1 分钟）
 * <p>- 有发送冷却时间（本项目 60 秒）
 * <p>- 重置成功后会吊销该用户所有 refreshToken（防止旧 token 继续刷新）
 */
@Service
public class PasswordResetService {

    private static final Duration CODE_TTL = Duration.ofMinutes(1);

    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);

    private final SysUserMapper sysUserMapper;

    private final AuthRefreshTokenMapper authRefreshTokenMapper;

    private final PasswordEncoder passwordEncoder;

    private final StringRedisTemplate redis;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final ObjectProvider<MailProperties> mailPropertiesProvider;

    public PasswordResetService(
            SysUserMapper sysUserMapper,
            AuthRefreshTokenMapper authRefreshTokenMapper,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redis,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            ObjectProvider<MailProperties> mailPropertiesProvider
    ) {
        this.sysUserMapper = sysUserMapper;
        this.authRefreshTokenMapper = authRefreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.mailSenderProvider = mailSenderProvider;
        this.mailPropertiesProvider = mailPropertiesProvider;
    }

    public void sendResetCode(PasswordResetSendCodeRequest request) {
        // 发送验证码逻辑：
        // 1）校验输入（username/email 二选一，或同时提供进行匹配校验）
        // 2）校验用户存在且启用
        // 3）校验邮件服务配置可用
        // 4）用 Redis setIfAbsent 做冷却限制（避免频繁发送）
        // 5）生成 6 位验证码，写入 Redis（带 TTL）
        // 6）发送邮件
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        String username = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : null;
        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;
        if (username == null && email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username/email required");
        }

        SysUser user;
        if (username != null) {
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            if (email != null) {
                String userEmail = StringUtils.hasText(user.getEmail()) ? user.getEmail().trim() : null;
                if (userEmail == null || !userEmail.equalsIgnoreCase(email)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email does not match username");
                }
            } else {
                email = StringUtils.hasText(user.getEmail()) ? user.getEmail().trim() : null;
                if (email == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email required");
                }
            }
        } else {
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        ensureMailReady();

        try {
            // setIfAbsent：如果冷却 key 已存在，说明刚刚发送过，拒绝重复发送。
            Boolean ok = redis.opsForValue().setIfAbsent(cooldownKey(email), "1", SEND_COOLDOWN);
            if (!Boolean.TRUE.equals(ok)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
            }
        } catch (RedisConnectionFailureException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Redis unavailable");
        }

        String code = genCode();
        try {
            // 验证码写 Redis，并设置 TTL：过期自动失效。
            redis.opsForValue().set(codeKey(email), code, CODE_TTL);
        } catch (RedisConnectionFailureException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Redis unavailable");
        }

        sendMail(email, code);
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        // 确认重置逻辑：
        // 1）校验参数（email + code + newPassword）
        // 2）从 Redis 读取验证码并校验
        // 3）查用户并更新 BCrypt 密码
        // 4）吊销该用户所有 refreshToken（强制所有设备重新登录）
        // 5）清理 Redis 中验证码 key
        if (request == null || !StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getVerificationCode())
                || !StringUtils.hasText(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email/verificationCode/newPassword required");
        }

        String email = request.getEmail().trim();
        String code = request.getVerificationCode().trim();
        String newPassword = request.getNewPassword();
        if (newPassword.length() < 6 || newPassword.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword length must be 6-50");
        }

        String expected;
        try {
            expected = redis.opsForValue().get(codeKey(email));
        } catch (RedisConnectionFailureException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Redis unavailable");
        }

        if (!StringUtils.hasText(expected) || !Objects.equals(expected, code)) {
            // 验证码错误或已过期
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid verification code");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        if (user == null || user.getId() == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        try {
            sysUserMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update password");
        }

        authRefreshTokenMapper.update(null, new LambdaUpdateWrapper<AuthRefreshToken>()
            .set(AuthRefreshToken::getRevoked, 1)
            .set(AuthRefreshToken::getLastUsedAt, LocalDateTime.now())
            .eq(AuthRefreshToken::getUserId, user.getId())
            .eq(AuthRefreshToken::getRevoked, 0));

        // 重置成功后吊销所有 refreshToken：
        // - 即使攻击者拿到了某个旧 refreshToken，也无法再刷新 AccessToken
        // - 用户需要重新登录获取新的 token pair

        try {
            redis.delete(codeKey(email));
        } catch (Exception ignored) {
        }
    }

    private void sendMail(String email, String code) {
        // 真实发送邮件：基于 Spring Boot 的 JavaMailSender。
        // 邮件的 From 通常来自 spring.mail.username。
        ensureMailReady();

        MailProperties mailProperties = mailPropertiesProvider == null ? null : mailPropertiesProvider.getIfAvailable();
        JavaMailSender sender = mailSenderProvider == null ? null : mailSenderProvider.getIfAvailable();

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setFrom(mailProperties.getUsername());
        msg.setSubject("密码重置验证码");
        msg.setText("你的验证码是：" + code + "\n有效期 1 分钟。\n如果不是本人操作请忽略此邮件。");
        sender.send(msg);
    }

    private void ensureMailReady() {
        // 发送前检查邮件配置：
        // - 避免生产环境忘记配置导致接口表面成功但实际上发不出去
        // - 也能给前端明确错误原因
        MailProperties mailProperties = mailPropertiesProvider == null ? null : mailPropertiesProvider.getIfAvailable();
        if (mailProperties == null || !StringUtils.hasText(mailProperties.getHost()) || !StringUtils.hasText(mailProperties.getUsername())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Mail not configured");
        }
        JavaMailSender sender = mailSenderProvider == null ? null : mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Mail sender not available");
        }
    }

    private String codeKey(String email) {
        return "pwdreset:code:" + email;
    }

    private String cooldownKey(String email) {
        return "pwdreset:cooldown:" + email;
    }

    private String genCode() {
        int n = ThreadLocalRandom.current().nextInt(0, 1000000);
        return String.format("%06d", n);
    }
}
