package com.communitysport.inspection.dto;

import java.time.LocalDateTime;
import java.util.List;

public class StaffInspectionDetailResponse {

    // 巡检上报详情响应 DTO（读模型）：
    // - 字段主要来自 staff_inspection_report 主表
    // - staffUsername 为展示字段：管理端列表/详情需要展示上报人用户名
    // - attachments 在 Service 中从 JSON 字符串反序列化为 List<String>
    //
    // 状态机口径：
    // - SUBMITTED：已提交（待处理）
    // - IN_PROGRESS：处理中
    // - RESOLVED：已解决（resolvedAt 通常非空）

    private Long id;

    private Long staffUserId;

    private String staffUsername;

    private String region;

    private String targetType;

    // 目标关联：
    // - targetType=VENUE => venueId 有值
    // - targetType=EQUIPMENT => equipmentId 有值
    // - targetType=OTHER => 两者可能都为空

    private Long venueId;

    private Long equipmentId;

    private String issueType;

    private String content;

    private List<String> attachments;

    private String status;

    // 时间字段口径：
    // - createdAt：上报时间
    // - updatedAt：最后一次变更时间（状态更新等）
    // - resolvedAt：解决时间（RESOLVED 时通常非空）

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStaffUserId() {
        return staffUserId;
    }

    public void setStaffUserId(Long staffUserId) {
        this.staffUserId = staffUserId;
    }

    public String getStaffUsername() {
        return staffUsername;
    }

    public void setStaffUsername(String staffUsername) {
        this.staffUsername = staffUsername;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
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
