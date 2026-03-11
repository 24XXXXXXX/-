package com.communitysport.coursebooking.dto;

import java.time.LocalDateTime;

public class CourseBookingDetailResponse {

    // 课程预约详情 DTO。
    //
    // 与 CourseBookingListItem 的区别：
    // - 详情页通常需要更完整的信息（例如教练处理时间、拒单原因、支付/核销时间等）
    // - 同样采用反范式化，把课程/教练/场馆/用户信息一并返回，减少前端拼装成本

    private Long id;

    private String bookingNo;

    private Long userId;

    private String username;

    private Long courseId;

    private String courseTitle;

    private String courseCoverUrl;

    private String coachUsername;

    private String venueName;

    private Long sessionId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer amount;

    private String status;

    private String verificationCode;

    // 核销码：
    // - 用于教练/工作人员核销时输入
    // - 前端展示时需注意安全边界（通常只有下单用户自己可见）

    private Boolean reviewed;

    // 是否已评价：用于详情页展示“评价入口/评价结果”。

    private LocalDateTime coachDecisionAt;

    private String rejectReason;

    private LocalDateTime paidAt;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookingNo() {
        return bookingNo;
    }

    public void setBookingNo(String bookingNo) {
        this.bookingNo = bookingNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getCourseCoverUrl() {
        return courseCoverUrl;
    }

    public void setCourseCoverUrl(String courseCoverUrl) {
        this.courseCoverUrl = courseCoverUrl;
    }

    public String getCoachUsername() {
        return coachUsername;
    }

    public void setCoachUsername(String coachUsername) {
        this.coachUsername = coachUsername;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
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

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public Boolean getReviewed() {
        return reviewed;
    }

    public void setReviewed(Boolean reviewed) {
        this.reviewed = reviewed;
    }

    public LocalDateTime getCoachDecisionAt() {
        return coachDecisionAt;
    }

    public void setCoachDecisionAt(LocalDateTime coachDecisionAt) {
        this.coachDecisionAt = coachDecisionAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
