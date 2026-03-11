package com.communitysport.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("coach_video_purchase")
public class CoachVideoPurchase {

    // 视频购买记录表（写模型）：
    // - 一条记录代表“某用户解锁了某个视频”的事实
    // - 典型会在数据库层建立 (user_id, video_id) 唯一约束，用于保证购买幂等
    //   （并发购买时依赖该约束触发 DuplicateKeyException，再回查补偿）
    //
    // 状态口径：
    // - status = PAID：表示已完成扣款（或免费解锁）并生效
    // - 统计/展示（购买次数、收益）一般只计算 PAID

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("purchase_no")
    private String purchaseNo;

    // purchaseNo：购买单号
    // - 面向前端/对账展示的稳定标识（比自增 id 更适合暴露）

    @TableField("user_id")
    private Long userId;

    @TableField("video_id")
    private Long videoId;

    private Integer amount;

    // amount：实际支付金额（与钱包系统单位一致）
    // - 由服务端基于视频价格计算并落库，避免前端篡改

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // createdAt：购买时间
    // - “我的购买记录”按此倒序
    // - 统计报表（某时间段购买数）也以该字段为口径

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
