package com.communitysport.video.dto;

import java.time.LocalDateTime;

public class VideoPurchaseListItem {

    // 购买记录列表项 DTO（读模型）：
    // - 用于用户端“我的购买记录”列表
    // - 该 DTO 通常由三部分数据拼装：
    //   1) purchase（购买记录：purchaseNo/amount/status/时间）
    //   2) video（视频信息：title/coverUrl/coachUserId）
    //   3) coach（教练展示：昵称/头像）
    // - 设计目标：前端拿到后可直接渲染，避免 N+1 接口请求

    private Long id;

    private String purchaseNo;

    private Long videoId;

    private String videoTitle;

    private String title;

    private String coverUrl;

    private Long coachUserId;

    private String coachName;

    private String coachAvatar;

    private Integer amount;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime purchasedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPurchaseNo() {
        return purchaseNo;
    }

    public void setPurchaseNo(String purchaseNo) {
        this.purchaseNo = purchaseNo;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Long getCoachUserId() {
        return coachUserId;
    }

    public void setCoachUserId(Long coachUserId) {
        this.coachUserId = coachUserId;
    }

    public String getCoachName() {
        return coachName;
    }

    public void setCoachName(String coachName) {
        this.coachName = coachName;
    }

    public String getCoachAvatar() {
        return coachAvatar;
    }

    public void setCoachAvatar(String coachAvatar) {
        this.coachAvatar = coachAvatar;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(LocalDateTime purchasedAt) {
        this.purchasedAt = purchasedAt;
    }
}
