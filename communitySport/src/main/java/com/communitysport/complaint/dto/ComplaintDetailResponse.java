package com.communitysport.complaint.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ComplaintDetailResponse {

    // 投诉详情响应 DTO（读模型）：
    // - 主体字段来自 complaint 主表
    // - messages 为会话流（complaint_message，按时间正序）
    // - attachments 在 Service 中从 JSON 字符串反序列化成 List<String>，前端可直接渲染
    // - username/assignedStaffUsername 为展示字段，避免前端再查用户表
    //
    // 状态机（常见口径）：
    // - SUBMITTED：用户提交，未开始处理
    // - ASSIGNED：已指派处理人
    // - IN_PROGRESS：处理中（任一方追加消息也可能推动进入该状态）
    // - RESOLVED：已解决（resolvedAt 非空）

    private Long id;

    private String complaintNo;

    private Long userId;

    private String username;

    private String complaintType;

    private String content;

    private List<String> attachments;

    private String status;

    private Long assignedStaffId;

    private String assignedStaffUsername;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    private List<ComplaintMessageItem> messages;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
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

    public List<ComplaintMessageItem> getMessages() {
        return messages;
    }

    public void setMessages(List<ComplaintMessageItem> messages) {
        this.messages = messages;
    }
}
