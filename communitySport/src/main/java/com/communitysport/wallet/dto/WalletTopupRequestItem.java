package com.communitysport.wallet.dto;

import java.time.LocalDateTime;

/**
 * 充值申请列表项 DTO。
 *
 * <p>用户侧：展示“我的充值申请”列表
 * <p>管理员侧：展示“所有充值申请”列表
 *
 * <p>状态流转（典型）：
 * <p>- PENDING（待审核）
 * <p>- APPROVED（已通过，已入账）
 * <p>- REJECTED（已拒绝）
 */
public class WalletTopupRequestItem {

    // 申请记录 id。
    private Long id;

    // 申请编号（对外展示）。
    private String requestNo;

    // 申请用户 id。
    private Long userId;

    // 申请用户名（管理员列表页展示用）。
    private String username;

    // 申请金额。
    private Integer amount;

    // 状态：PENDING/APPROVED/REJECTED。
    private String status;

    // 备注：用户申请时可填；管理员处理时也可能覆盖/追加。
    private String remark;

    // 申请时间。
    private LocalDateTime requestedAt;

    // 处理人（管理员 userId）。
    private Long processedBy;

    // 处理时间。
    private LocalDateTime processedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Long getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(Long processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
