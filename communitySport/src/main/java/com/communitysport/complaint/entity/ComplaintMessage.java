package com.communitysport.complaint.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("complaint_message")
public class ComplaintMessage {

    // 工单消息表（写模型）：
    // - 记录用户与处理人员之间的沟通消息
    // - 通过 complaintId 归属到某一条投诉（工单）
    //
    // 关键字段：
    // - senderRole：USER/STAFF/ADMIN，用于区分消息来源与前端渲染
    // - attachments：附件 URL 列表 JSON 字符串（接口层会反序列化为 List<String>）
    // - createdAt：消息时间；详情页通常按 id/createdAt 升序展示成对话流

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("complaint_id")
    private Long complaintId;

    @TableField("sender_user_id")
    private Long senderUserId;

    @TableField("sender_role")
    private String senderRole;

    private String content;

    private String attachments;

    @TableField("created_at")
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
