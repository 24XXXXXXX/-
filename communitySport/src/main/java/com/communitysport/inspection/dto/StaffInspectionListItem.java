package com.communitysport.inspection.dto;

import java.time.LocalDateTime;

public class StaffInspectionListItem {

    // 巡检上报列表项 DTO（读模型）：
    // - 员工端：查看“我的上报”列表（staffUsername 可能为空，因为员工端不一定需要显示）
    // - 管理端：查看全量上报列表（包含 staffUsername/region 用于展示与筛选）
    //
    // 说明：
    // - content 在列表场景通常作为摘要展示（详情页才展示完整附件/更多信息）
    // - createdAt/updatedAt/resolvedAt 用于列表排序和生命周期展示

    private Long id;

    private Long staffUserId;

    private String staffUsername;

    private String region;

    // region：上报人所属片区（来自 staff_profile）
    // - 用于管理端按片区筛选上报、以及日报统计

    private String targetType;

    // 目标关联字段：
    // - targetType=VENUE => venueId 有值
    // - targetType=EQUIPMENT => equipmentId 有值

    private Long venueId;

    private Long equipmentId;

    private String issueType;

    private String content;

    private String status;

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
