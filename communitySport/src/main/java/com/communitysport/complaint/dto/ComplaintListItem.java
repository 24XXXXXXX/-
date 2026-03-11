package com.communitysport.complaint.dto;

import java.time.LocalDateTime;

public class ComplaintListItem {

    // 投诉列表项 DTO（读模型）：
    // - 用户端：我的投诉列表
    // - 管理端：投诉工单列表（可筛选/分页）
    // - 员工端：分配给我/可领取的投诉列表
    //
    // 特点：
    // - username/assignedStaffUsername 为展示字段，Service 会批量查询用户表组装，避免 N+1
    // - createdAt/updatedAt/resolvedAt 用于列表排序与工单生命周期展示

    private Long id;

    private String complaintNo;

    private Long userId;

    private String username;

    private String complaintType;

    private String status;

    private Long assignedStaffId;

    private String assignedStaffUsername;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComplaintNo() {
        return complaintNo;
    }

    public void setComplaintNo(String complaintNo) {
        this.complaintNo = complaintNo;
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

    public String getComplaintType() {
        return complaintType;
    }

    public void setComplaintType(String complaintType) {
        this.complaintType = complaintType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssignedStaffId() {
        return assignedStaffId;
    }

    public void setAssignedStaffId(Long assignedStaffId) {
        this.assignedStaffId = assignedStaffId;
    }

    public String getAssignedStaffUsername() {
        return assignedStaffUsername;
    }

    public void setAssignedStaffUsername(String assignedStaffUsername) {
        this.assignedStaffUsername = assignedStaffUsername;
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

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
