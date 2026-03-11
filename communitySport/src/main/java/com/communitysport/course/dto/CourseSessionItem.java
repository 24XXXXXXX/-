package com.communitysport.course.dto;

import java.time.LocalDateTime;

public class CourseSessionItem {

    // 课次 ID。
    // 前端在创建预约（booking）时通常会把 sessionId 作为唯一指向“某一节课”的参数传回后端。
    private Long id;

    // 归属课程 ID（courseId）。
    // 对于“从课次反查课程”或列表渲染时做关联跳转会有用。
    private Long courseId;

    // 上课开始/结束时间。
    // date 维度的筛选口径通常以 startTime 落在 [date, date+1) 作为“属于某天”。
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    // 名额上限与已占用名额。
    // - capacity：最多可报名人数
    // - enrolledCount：当前已占座/已报名人数（并发下会通过条件更新防止超卖）
    private Integer capacity;

    private Integer enrolledCount;

    // 课次状态：
    // - OPEN：可报名
    // - CLOSED：停止报名
    // - CANCELED：取消课次
    // 用户端通常只会看到 OPEN；教练端管理页可能看到全部。
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getEnrolledCount() {
        return enrolledCount;
    }

    public void setEnrolledCount(Integer enrolledCount) {
        this.enrolledCount = enrolledCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
