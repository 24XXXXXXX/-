package com.communitysport.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 服务：负责“创建 Token”和“解析校验 Token”。
 *
 * <p>本项目使用 AccessToken + RefreshToken（双 Token）策略：
 * <p>- AccessToken：有效期短（分钟级），用于访问业务接口
 * <p>- RefreshToken：有效期长（天级），用于换取新的 AccessToken
 *
 * <p>为什么要区分两种 Token？
 * <p>- 如果只用一个长效 Token，一旦泄露风险窗口很大
 * <p>- 双 Token 让 AccessToken 过期更快，降低被盗用的影响面
 */
@Service
public class JwtService {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_TYPE = "token_type";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(Long userId, String username, List<String> roleCodes) {
        // AccessToken：一般设置较短过期时间（例如 15~60 分钟），减少泄露风险。
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(properties.getAccessTokenTtlMinutes()));

        return Jwts.builder()
            // jti：Token 唯一 ID（可用于黑名单/日志追踪等扩展）
            .id(UUID.randomUUID().toString())
            // subject：主体信息，这里使用用户名
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claims(Map.of(
                CLAIM_UID, userId,
                CLAIM_ROLES, roleCodes,
                CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS
            ))
            // HS256 对称加密签名：服务端用同一个 secret 生成与校验
            .signWith(getKey())
            .compact();
    }

    public String createRefreshToken(Long userId, String username) {
        // RefreshToken：有效期通常更长（天级），只用于刷新 AccessToken，不用于访问业务接口。
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofDays(properties.getRefreshTokenTtlDays()));

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claims(Map.of(
                CLAIM_UID, userId,
                CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH
            ))
            .signWith(getKey())
            .compact();
    }

    public Claims parseAndValidate(String jwt) {
        // parseAndValidate：不仅解析 JWT，还会验证签名与过期时间。
        // 如果 token 被篡改、签名不匹配、已过期，会抛出异常。
        return Jwts.parser()
            .verifyWith(getKey())
            .build()
            .parseSignedClaims(jwt)
            .getPayload();
    }

    private SecretKey getKey() {
        // HS256 签名密钥要求：至少 256 bit（32 bytes）
        // 这里从配置 app.jwt.secret 读取。
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret is required");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
