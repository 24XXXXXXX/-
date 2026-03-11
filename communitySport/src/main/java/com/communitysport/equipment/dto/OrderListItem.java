package com.communitysport.equipment.dto;

import java.time.LocalDateTime;

public class OrderListItem {

    // 用户侧订单列表项 DTO（轻量读模型）：
    // - 用于“我的订单”列表页
    // - 只包含列表展示所需字段（不包含明细 items），减少网络传输

    private Long id;

    private String orderNo;

    private Integer totalAmount;

    private String status;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
