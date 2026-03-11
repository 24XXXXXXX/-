package com.communitysport.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.communitysport.security.AuthenticatedUser;

/**
 * JWT 鉴权过滤器（每个请求只执行一次）。
 *
 * <p>它的职责非常明确：
 * <p>1）从 HTTP Header 中读取 Authorization: Bearer <token>
 * <p>2）验证并解析 JWT（由 JwtService 完成）
 * <p>3）如果是合法的 AccessToken，则把用户身份与角色写入 Spring Security 的 SecurityContext
 * <p>4）继续放行请求（最终到 Controller）
 *
 * <p>注意：
 * <p>- 本项目使用“双 Token”，因此这里需要识别 token_type：只接受 access token 作为“访问接口”的凭证。
 * <p>- refresh token 只用于 /api/auth/refresh 换取新的 access token，不应该用于访问业务接口。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 约定：前端通过 Authorization 头携带 JWT。
        // 示例：Authorization: Bearer eyJhbGciOi...
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            try {
                // 解析并校验 JWT（签名、过期时间等）。
                Claims claims = jwtService.parseAndValidate(token);
                String tokenType = claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class);
                if (JwtService.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                    // AccessToken：可用于访问业务接口。
                    String username = claims.getSubject();
                    Number uidNum = claims.get(JwtService.CLAIM_UID, Number.class);
                    Long userId = uidNum == null ? null : uidNum.longValue();

                    // 从 JWT 中取出 roles，映射成 Spring Security 可识别的 GrantedAuthority。
                    // 约定：统一加 ROLE_ 前缀（例如 ADMIN -> ROLE_ADMIN）。
                    Collection<? extends GrantedAuthority> authorities = toAuthorities(claims.get(JwtService.CLAIM_ROLES));

                    // 把认证信息写入 SecurityContext：
                    // - principal：AuthenticatedUser（封装 userId/username）
                    // - credentials：这里不需要密码，所以传 null
                    // - authorities：角色列表
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, username), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // 任何解析失败（token 伪造/过期/格式错误）都不在这里抛异常。
                // 原因：
                // 1）让请求继续往后走，由 Spring Security 在访问受保护资源时统一返回 401。
                // 2）避免把具体的 Token 错误原因暴露给客户端（安全考虑）。
            }
        }

        // 无论有没有 token / token 是否有效，都要继续执行后续过滤器。
        // - 如果 SecurityContext 没有认证信息，访问受保护接口会被拦截（401）。
        // - 如果是公开接口，依然可以正常访问。
        filterChain.doFilter(request, response);
    }

    private Collection<? extends GrantedAuthority> toAuthorities(Object rolesObj) {
        // JWT 中的 roles 通常是一个数组/列表。
        // 这里把它转换成 Spring Security 的 GrantedAuthority 集合，用于后续的权限判断。
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (rolesObj instanceof List<?> roles) {
            for (Object r : roles) {
                if (r == null) {
                    continue;
                }
                String roleCode = String.valueOf(r);
                String authority = roleCode.startsWith("ROLE_") ? roleCode : ("ROLE_" + roleCode);
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        }
        return authorities;
    }
}
