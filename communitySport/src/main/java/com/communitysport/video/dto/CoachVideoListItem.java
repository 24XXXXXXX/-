package com.communitysport.video.dto;

import java.time.LocalDateTime;

public class CoachVideoListItem {

    // 视频列表项 DTO（读模型）：
    // - 用户端“公开视频目录”与教练端“我的视频”均复用该结构
    // - purchased 为用户态字段：
    //   - 匿名访问时返回 null（而不是 false），避免前端误解为“未购买”
    // - purchaseCount 为运营/热度字段：通常只统计 status=PAID 的购买记录

    private Long id;

    private Long coachUserId;

    private String coachUsername;

    private String title;

    private String category;

    private Integer price;

    private String coverUrl;

    private String status;

    private Boolean purchased;

    private Long purchaseCount;

    private LocalDateTime createdAt;

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

    public String getCoachUsername() {
        return coachUsername;
    }

    public void setCoachUsername(String coachUsername) {
        this.coachUsername = coachUsername;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getPurchased() {
        return purchased;
    }

    public void setPurchased(Boolean purchased) {
        this.purchased = purchased;
    }

    public Long getPurchaseCount() {
        return purchaseCount;
    }

    public void setPurchaseCount(Long purchaseCount) {
        this.purchaseCount = purchaseCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
