package com.communitysport.course.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.communitysport.course.dto.CourseDetailResponse;
import com.communitysport.course.dto.CoursePageResponse;
import com.communitysport.course.service.CourseService;

@RestController
public class CourseCatalogController {

    private final CourseService courseService;

    public CourseCatalogController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/api/courses")
    public CoursePageResponse list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "coachUserId", required = false) Long coachUserId,
            @RequestParam(name = "venueId", required = false) Long venueId
    ) {
        // 课程“公开目录”列表页（面向用户/游客）。
        //
        // 典型页面：
        // - 用户端课程广场/课程列表
        // - 支持按关键字、分类、教练、场馆等维度筛选
        //
        // 参数语义：
        // - page/size：分页参数（服务层会做默认值与上限保护）
        // - keyword：对课程标题做模糊匹配（用于搜索）
        // - category：课程分类精确匹配（用于“分类筛选”下拉框）
        // - coachUserId：只看某个教练发布的课程
        // - venueId：只看某个场馆下的课程
        //
        // 重要约束：
        // - 公开列表只返回上架状态（ON_SALE）的课程；下架/草稿不会出现在这里。
        return courseService.listPublic(page, size, keyword, category, coachUserId, venueId);
    }

    @GetMapping("/api/courses/{id:\\d+}")
    public CourseDetailResponse detail(@PathVariable("id") Long id) {
        // 课程公开详情页。
        //
        // 说明：
        // - 只允许访问 ON_SALE 的课程；否则按“未找到”处理，避免泄露教练端草稿信息。
        return courseService.getPublic(id);
    }

    @GetMapping("/api/courses/categories")
    public List<String> categories() {
        // 课程公开分类列表。
        //
        // 用途：
        // - 前端筛选条件的“分类下拉框”
        //
        // 口径：
        // - 只统计 ON_SALE 课程中出现过的分类
        // - 去重 + 排序由服务层完成
        return courseService.listDistinctPublicCategories();
    }
}
