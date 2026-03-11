package com.communitysport.inspection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("staff_inspection_report")
public class StaffInspectionReport {

    // 员工巡检上报主表（写模型）：
    // - 一条记录代表员工提交的一次“巡检/隐患/问题”上报
    // - 可针对不同目标（场地/器材/其它）进行分类，并附带问题类型与描述
    //
    // 字段口径：
    // - targetType：目标类型（例如 VENUE/EQUIPMENT/OTHER），Service 侧做白名单校验
    // - issueType：问题类型（例如 MAINTENANCE/REPAIR/SHORTAGE/DAMAGE/OTHER），Service 侧白名单校验
    // - attachments：附件 URL 列表的 JSON 字符串（接口层以 List<String> 读写）
    // - status：状态机（SUBMITTED -> IN_PROGRESS -> RESOLVED）
    // - region：员工所属片区（来自 staff_profile），用于管理端按片区筛选
    //
    // 时间字段：
    // - createdAt：上报时间
    // - updatedAt：最后一次变更时间（状态更新等）
    // - resolvedAt：解决时间（RESOLVED 时非空；回退到非 RESOLVED 时可能清空）

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("staff_user_id")
    private Long staffUserId;

    private String region;

    @TableField("target_type")
    private String targetType;

    @TableField("venue_id")
    private Long venueId;

    @TableField("equipment_id")
    private Long equipmentId;

    @TableField("issue_type")
    private String issueType;

    private String content;

    private String attachments;

    private String status;

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

    public Long getStaffUserId() {
        return staffUserId;
    }

    public void setStaffUserId(Long staffUserId) {
        this.staffUserId = staffUserId;
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
