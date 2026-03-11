package com.communitysport.course.controller;

import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.course.dto.CourseCreateRequest;
import com.communitysport.course.dto.CourseDetailResponse;
import com.communitysport.course.dto.CoursePageResponse;
import com.communitysport.course.dto.CourseStatusRequest;
import com.communitysport.course.dto.CourseUpdateRequest;
import com.communitysport.course.service.CourseService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class CoachCourseController {

    private final CourseService courseService;

    public CoachCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping("/api/coach/courses")
    public CourseDetailResponse create(Authentication authentication, @RequestBody CourseCreateRequest request) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：创建课程。
        //
        // 角色边界：
        // - 只有 ROLE_COACH 才能发布课程（相当于“供给侧”能力）
        // - 普通用户只能在公开目录浏览/下单，不允许直接写 coach_course
        //
        // 说明：
        // - controller 显式 requireCoach 是“运行时兜底”（即使 SecurityConfig 漏配，也会在这里拦住）
        return courseService.create(principal, request);
    }

    @PutMapping("/api/coach/courses/{id}")
    public CourseDetailResponse update(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody CourseUpdateRequest request
    ) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：修改课程信息。
        //
        // 安全性：
        // - service 会校验“课程归属（coach_user_id 必须等于当前登录用户）”
        // - 避免越权修改他人课程
        return courseService.update(principal, id, request);
    }

    @PostMapping("/api/coach/courses/{id}/status")
    public CourseDetailResponse updateStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody CourseStatusRequest request
    ) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：上下架课程。
        //
        // 约束：
        // - ON_SALE：公开可见（出现在 /api/courses）
        // - OFF_SALE：公开不可见（隐藏），但教练仍可在自己的列表中管理
        return courseService.updateStatus(principal, id, request);
    }

    @GetMapping("/api/coach/courses")
    public CoursePageResponse myCourses(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：查看“我发布的课程”列表。
        //
        // 与公开目录的区别：
        // - 这里会返回教练自己的所有课程（包括 OFF_SALE）
        // - 公开目录只返回 ON_SALE
        return courseService.myCourses(principal, page, size, status);
    }

    @GetMapping("/api/coach/courses/{id}")
    public CourseDetailResponse myDetail(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser principal = getPrincipal(authentication);
        requireCoach(authentication);
        // 教练端：查看“我的课程”详情。
        //
        // 说明：
        // - 可以查看 OFF_SALE 的课程（用于编辑/再次上架）
        return courseService.myCourseDetail(principal, id);
    }

    @GetMapping("/api/coach/courses/categories")
    public List<String> categories(Authentication authentication) {
        requireCoach(authentication);
        // 教练端：查询“系统中所有出现过的分类”。
        //
        // 与公开分类接口的区别：
        // - 教练端接口不限定 ON_SALE（用于运营/编辑时的分类参考）
        return courseService.listDistinctCategories();
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

    private void requireCoach(Authentication authentication) {
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
