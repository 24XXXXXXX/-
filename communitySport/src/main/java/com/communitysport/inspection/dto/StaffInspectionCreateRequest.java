package com.communitysport.inspection.dto;

import java.util.List;

public class StaffInspectionCreateRequest {

    // 员工巡检上报创建请求 DTO（写模型）：
    // - targetType：目标类型（VENUE/EQUIPMENT/OTHER）
    //   - 当 targetType=VENUE 时要求 venueId 非空
    //   - 当 targetType=EQUIPMENT 时要求 equipmentId 非空
    // - issueType/content：问题分类与描述（长度/空白校验在 Service）
    // - attachments：附件 URL 列表；Service 会序列化为 JSON 字符串存库
    // - newVenueStatus：可选；当目标为 VENUE 时，允许在上报时联动修改场地状态（由 Service 调用 VenueService）

    private String targetType;

    // 与 targetType 的关系：
    // - targetType=VENUE 时填写 venueId
    // - targetType=EQUIPMENT 时填写 equipmentId
    // - targetType=OTHER 时两者都可不填

    private Long venueId;

    private Long equipmentId;

    private String issueType;

    private String content;

    private List<String> attachments;

    private String newVenueStatus;

    // newVenueStatus：仅当 targetType=VENUE 时生效
    // - Service 会调用 VenueService.updateStatus 进行联动更新

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

    public String getNewVenueStatus() {
        return newVenueStatus;
    }

    public void setNewVenueStatus(String newVenueStatus) {
        this.newVenueStatus = newVenueStatus;
    }
}
