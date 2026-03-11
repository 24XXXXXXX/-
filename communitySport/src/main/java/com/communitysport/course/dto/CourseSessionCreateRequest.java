package com.communitysport.course.dto;

import java.time.LocalDateTime;

public class CourseSessionCreateRequest {

    // 课次开始时间（上课开始）。
    // 由 service 统一校验 startTime < endTime，避免 controller/前端各自实现导致口径不一致。
    private LocalDateTime startTime;

    // 课次结束时间（上课结束）。
    private LocalDateTime endTime;

    // 可报名名额（可选）。
    // 若不传，service 会默认沿用课程（CoachCourse）的 capacity；课程也未配置时再兜底为 1。
    // 这么设计的目的：
    // - 课程层 capacity 代表“常规开班规模”
    // - 课次层 capacity 允许对单次排期做临时扩容/缩容
    private Integer capacity;

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
}
