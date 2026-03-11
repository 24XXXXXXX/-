package com.communitysport.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 已认证但无权限（403）处理器。
 *
 * <p>典型场景：
 * <p>- 普通用户访问 /api/admin/**
 * <p>- 员工访问只有管理员才能操作的接口
 *
 * 这类请求“身份是有效的”，但“权限不足”，因此返回 403。
 *
 * <p>同样为了前端统一处理，这里输出 JSON：{code,msg}。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        // 403：已登录但没有访问权限
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":403,\"msg\":\"FORBIDDEN\"}");
    }
}
