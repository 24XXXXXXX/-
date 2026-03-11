package com.communitysport.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 未认证（401）处理器。
 *
 * <p>当用户访问“需要登录”的接口，但：
 * <p>- 没有携带 Authorization
 * <p>- 或者携带的 AccessToken 无效/过期
 *
 * Spring Security 会触发 AuthenticationEntryPoint，由它负责输出响应。
 *
 * <p>为什么要自定义？
 * <p>- 默认行为可能是返回 HTML 或者返回空内容，不利于前端统一提示
 * <p>- 前后端分离项目通常约定统一 JSON：{code, msg}
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        // 401：未登录/未认证
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"msg\":\"UNAUTHORIZED\"}");
    }
}
