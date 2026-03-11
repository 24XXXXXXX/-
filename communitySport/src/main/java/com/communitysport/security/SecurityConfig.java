package com.communitysport.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用强度 12 的 BCrypt，兼顾安全与性能
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler
    ) throws Exception {
        // ===== 1）基础安全策略 =====
        // 本项目是“前后端分离 + JWT 鉴权”的典型 REST API 服务：
        // - 前端通过 Authorization: Bearer <AccessToken> 访问接口
        // - 服务端不使用 Session（无状态），每次请求都从 Token 解析身份
        http
            // REST API 通常不使用浏览器表单提交的 CSRF 保护，因此禁用 CSRF。
            // 如果你以后要把接口开放给浏览器表单或 Cookie 鉴权，需要重新评估。
            .csrf(csrf -> csrf.disable())
            // 开启 CORS：允许前端域名跨域访问后端接口（开发时常见：localhost:3000 -> localhost:8080）。
            // 具体允许的 origin/header/method 一般在全局 CORS 配置或 Spring 默认策略中定义。
            .cors(Customizer.withDefaults())
            // 无状态：Spring Security 不创建/使用 HTTP Session。
            // 这是 JWT 的关键前提：服务端不保存登录会话，提升扩展性（多实例）但需要更谨慎管理 Token。
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh
                // 未登录/Token 无效时的统一返回（通常返回 401 + JSON）
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                // 已登录但无权限访问时的统一返回（通常返回 403 + JSON）
                .accessDeniedHandler(restAccessDeniedHandler)
            )
            // 禁用 httpBasic/formLogin：我们不走“浏览器弹窗登录/表单登录”，而是走 JSON + JWT。
            .httpBasic(hb -> hb.disable())
            .formLogin(fl -> fl.disable())
            .authorizeHttpRequests(auth -> auth
                // ===== 2）匿名放行（无需登录即可访问） =====
                // 这类接口通常是：登录/注册/刷新 token、健康检查、Swagger 文档等。
                .requestMatchers(
                    "/api/auth/**",
                    "/api/health",
                    "/error",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // 仅放行部分 GET：用于“公开浏览”的内容（场地/课程/器材/视频/公告等）。
                // 这些接口返回的是展示数据，不涉及个人隐私和资金变更。
                .requestMatchers(HttpMethod.GET,
                    "/api/venue/types",
                    "/api/venues/**",
                    "/api/venues/*/reviews",
                    "/api/venues/*/timeslots",
                    "/api/home-banners",
                    "/api/home/recommendations",
                    "/api/courses",
                    "/api/courses/**",
                    "/api/courses/*/sessions",
                    "/api/equipment/categories",
                    "/api/equipments",
                    "/api/equipments/**",
                    "/api/videos",
                    "/api/videos/**",
                    "/api/notices",
                    "/api/notices/**",
                    "/api/favorites/count",
                    "/upload/**",
                    "/video/**"
                ).permitAll()
                // ===== 3）其余所有接口默认需要登录 =====
                // 角色权限（ADMIN/STAFF/COACH/USER）通常在各 Controller 层面进一步控制。
                .anyRequest().authenticated()
            );

        // ===== 4）JWT 过滤器挂载位置 =====
        // JwtAuthenticationFilter 会在每个请求到达 Controller 之前执行：
        // - 从 Authorization Header 解析 AccessToken
        // - 校验 Token 合法性/过期
        // - 解析用户身份与角色并写入 SecurityContext
        //
        // 把它放在 UsernamePasswordAuthenticationFilter 之前：
        // - 避免走表单登录流程
        // - 更早建立认证信息，后续鉴权才能生效
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
