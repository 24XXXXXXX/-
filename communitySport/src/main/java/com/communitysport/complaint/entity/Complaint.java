package com.communitysport.complaint.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("complaint")
public class Complaint {

    // 投诉/工单主表（写模型）：
    // - 一条记录代表用户发起的一次投诉
    // - 后续沟通消息存于 complaint_message（1:N）
    //
    // 关键字段：
    // - complaintNo：对外展示的单号（比自增 id 更适合暴露）
    // - attachments：附件 URL 列表的 JSON 字符串（详情接口会反序列化为 List）
    // - status：工单状态机
    //   - SUBMITTED：用户提交
    //   - ASSIGNED：已指派/待处理
    //   - IN_PROGRESS：处理中
    //   - RESOLVED：已解决（resolvedAt 非空）
    // - assignedStaffId：处理人
    //   - null/0 代表“未指派/未领取”，员工端可能在回复时自动领取
    //
    // 时间字段口径：
    // - createdAt：提交时间（提交量统计口径）
    // - updatedAt：最后一次变更时间（包含追加消息/指派/状态变更）
    // - resolvedAt：解决时间（解决量统计口径）

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("complaint_no")
    private String complaintNo;

    @TableField("user_id")
    private Long userId;

    @TableField("complaint_type")
    private String complaintType;

    private String content;

    private String attachments;

    private String status;

    @TableField("assigned_staff_id")
    private Long assignedStaffId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("resolved_at")
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

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
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
