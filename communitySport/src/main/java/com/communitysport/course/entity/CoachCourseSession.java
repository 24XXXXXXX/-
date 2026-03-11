package com.communitysport.course.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("coach_course_session")
public class CoachCourseSession {

    // 课程课次/排期表（coach_course_session）。
    //
    // 这是课程预约中的“核心资源（seat resource）”：
    // - capacity：可报名名额上限
    // - enrolled_count：当前已占用名额
    // - status：是否开放报名
    //
    // 并发提示：
    // - 多人同时预约同一课次时，会并发更新 enrolled_count
    // - 因此 service 会通过 mapper 的“条件更新（CAS）”来占座，防止超卖

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private Long courseId;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    private Integer capacity;

    // 名额上限。

    @TableField("enrolled_count")
    private Integer enrolledCount;

    // 已报名/已占座人数。

    private String status;

    // 课次状态：
    // - OPEN：开放报名
    // - CLOSED：关闭报名（保留排期，但不允许新预约）
    // - CANCELED：取消本节课（通常用于临时停课）

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
