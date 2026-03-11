package com.communitysport.video.dto;

import java.time.LocalDateTime;

public class VideoPurchaseResponse {

    // 购买/解锁接口响应 DTO（读模型）：
    // - 返回本次（或已存在的）购买记录
    // - purchaseNo 便于前端展示与对账
    // - amount/status/createdAt 反映购买结果与时间

    private Long id;

    private String purchaseNo;

    private Long userId;

    private Long videoId;

    private Integer amount;

    private String status;

    private LocalDateTime createdAt;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
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
}
