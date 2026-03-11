package com.communitysport.coursebooking.dto;

import java.time.LocalDateTime;

public class CourseBookingListItem {

    // 课程预约列表项 DTO。
    //
    // 设计目标：
    // - 面向“列表页/订单页”渲染，一次返回前端需要的主要展示字段
    // - 通过反范式化（把用户名、课程标题、场馆名、教练名等拼好）避免前端 N+1 次额外请求
    //
    // 特别说明：
    // - status 决定按钮/操作（待教练确认、待支付、待核销、已完成等）
    // - reviewed 表示是否已评价（通常由 review 表反查得出，用于显示“去评价/已评价”）

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

    // 核销码（6位随机码）：
    // - 线下核销场景可让用户出示/报出
    // - 由于随机码可能重复，核销接口通常推荐配合 bookingNo 使用

    private Boolean reviewed;

    // 是否已评价。

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
