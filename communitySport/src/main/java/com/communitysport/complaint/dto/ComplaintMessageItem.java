package com.communitysport.complaint.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ComplaintMessageItem {

    // 工单消息项 DTO（读模型）：
    // - senderRole：USER/STAFF/ADMIN，用于前端渲染不同气泡样式与权限控制
    // - senderUsername：服务端通过 senderUserId 批量回查 user 表后填充
    // - attachments：已反序列化的附件 URL 列表（避免前端自行解析 JSON 字符串）

    private Long id;

    private Long complaintId;

    private Long senderUserId;

    private String senderRole;

    private String senderUsername;

    private String content;

    private List<String> attachments;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(Long complaintId) {
        this.complaintId = complaintId;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(Long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
