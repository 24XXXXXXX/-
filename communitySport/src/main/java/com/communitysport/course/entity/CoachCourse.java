package com.communitysport.course.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("coach_course")
public class CoachCourse {

    // 教练发布的“课程”主表（coach_course）。
    //
    // 课程与课次的关系：
    // - CoachCourse：描述课程本身（标题、价格、默认容量、封面、简介、所属场馆等）
    // - CoachCourseSession：描述具体哪一天/哪一节课（start/end、可报名状态、已报名人数等）
    //
    // 课程与预约的关系：
    // - 预约（coach_course_booking）绑定到 session，而不是直接绑定到 course
    //   因为预约必须精确到具体上课时间段。

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("coach_user_id")
    private Long coachUserId;

    // 发布该课程的教练用户 ID。

    private String title;

    private String category;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    private Integer price;

    // 课程价格：与钱包金额单位一致（通常为“分”）。

    @TableField("cover_url")
    private String coverUrl;

    @TableField("venue_id")
    private Long venueId;

    // 上课场馆（可选）。
    // - 用于展示地点与在公开目录按场馆筛选

    private Integer capacity;

    // 默认容量：
    // - 作为新建 session 的默认 capacity（session 也可以单独覆盖）

    private String outline;

    private String status;

    // 上下架状态：
    // - ON_SALE：公开可见，可被预约
    // - OFF_SALE：公开不可见（不删除数据，便于再次上架/追溯历史）

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // created_at/updated_at：
    // - created_at 用于运营统计与排序
    // - updated_at 用于教练端编辑后刷新显示

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCoachUserId() {
        return coachUserId;
    }

    public void setCoachUserId(Long coachUserId) {
        this.coachUserId = coachUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
