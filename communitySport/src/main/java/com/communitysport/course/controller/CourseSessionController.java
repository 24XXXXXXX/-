package com.communitysport.course.controller;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.course.dto.CourseSessionCreateRequest;
import com.communitysport.course.dto.CourseSessionItem;
import com.communitysport.course.dto.CourseSessionStatusRequest;
import com.communitysport.course.service.CourseSessionService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CourseSessionController {

    private final CourseSessionService courseSessionService;

    public CourseSessionController(CourseSessionService courseSessionService) {
        this.courseSessionService = courseSessionService;
    }

    @GetMapping("/api/courses/{courseId}/sessions")
    public List<CourseSessionItem> publicList(
            @PathVariable("courseId") Long courseId,
            @RequestParam(name = "date", required = false) LocalDate date
    ) {
        // 课次（session）公开列表（用户端/无需教练身份）。
        //
        // 这组接口的定位是“给用户展示可报名的课次”，因此：
        // - service 内会把口径收紧为：课程必须是 ON_SALE、课次必须是 OPEN
        // - controller 只负责接收参数与转发调用，不在这里重复写业务口径
        //
        // 参数说明：
        // - courseId：课程 ID
        // - date：可选，查询某一天的课次。实际会按 start_time 落在 [date, date+1) 的半开区间过滤
        return courseSessionService.listPublic(courseId, date);
    }

    @PostMapping("/api/coach/courses/{courseId}/sessions")
    public CourseSessionItem create(
            Authentication authentication,
            @PathVariable("courseId") Long courseId,
            @RequestBody CourseSessionCreateRequest request
    ) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：为某个课程创建课次排期（上课时间段）。
        //
        // 这里的安全/职责分层：
        // - controller 负责“你是不是教练”（ROLE_COACH）这种粗粒度角色校验
        // - service 负责“这门课是不是你的”（course.coachUserId == principal.userId）这种细粒度资源归属校验
        //
        // 这么拆分的好处：
        // - controller 层统一做角色门禁，避免忘记在某个入口加 @PreAuthorize 等
        // - service 层集中做业务校验，避免不同入口校验逻辑不一致
        return courseSessionService.create(principal, courseId, request);
    }

    @GetMapping("/api/coach/courses/{courseId}/sessions")
    public List<CourseSessionItem> mySessions(
            Authentication authentication,
            @PathVariable("courseId") Long courseId,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(name = "status", required = false) String status
    ) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：查看“我的课程”的课次列表。
        //
        // 与公开列表的区别：
        // - 教练端允许查看 CLOSED/CANCELED 等内部排期（用于管理、复盘、后续对账/核销等）
        // - 可按 status 过滤（OPEN/CLOSED/CANCELED）
        // - date 过滤仍采用 [date, date+1) 的半开区间，保持前后端口径一致
        return courseSessionService.mySessions(principal, courseId, date, status);
    }

    @PostMapping("/api/coach/course-sessions/{id}/status")
    public CourseSessionItem updateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody CourseSessionStatusRequest request
    ) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：修改单个课次的状态。
        //
        // 常见场景：
        // - 临时关闭报名（OPEN -> CLOSED）
        // - 取消本节课（-> CANCELED）
        //
        // 注意：
        // - controller 只做角色校验；具体允许的状态值/归属校验在 service 内完成
        return courseSessionService.updateStatus(principal, id, request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 从 Spring Security 的 Authentication 中取出业务层需要的 AuthenticatedUser。
        //
        // 这里把“鉴权失败”统一转换为 401：
        // - 未登录
        // - token 解析失败
        // - principal 类型不符合预期
        //
        // 这样上层 controller/service 不用重复写空指针判断。
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        return au;
    }

    private void requireCoach(Authentication authentication) {
        // 运行时角色门禁：必须拥有 ROLE_COACH。
        //
        // 为什么不用注解而是在 controller 内手写：
        // - 项目里采用“显式 requireXxx”风格，便于读代码时一眼看出权限要求
        // - 也便于统一返回错误文案（这里返回 403 + “需要教练认证”）
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean ok = false;
        if (authorities != null) {
            for (GrantedAuthority a : authorities) {
                if (a == null) {
                    continue;
                }
                String auth = a.getAuthority();
                if (StringUtils.hasText(auth) && "ROLE_COACH".equals(auth)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要教练认证");
        }
    }
}
