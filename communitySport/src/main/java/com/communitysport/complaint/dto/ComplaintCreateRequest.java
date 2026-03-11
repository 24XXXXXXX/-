package com.communitysport.complaint.dto;

import java.util.List;

public class ComplaintCreateRequest {

    // 创建投诉请求 DTO（用户端写模型）：
    // - complaintType：投诉类型（Service 中有白名单校验，例如 VENUE/EQUIPMENT/COURSE/OTHER）
    // - content：投诉内容（Service 中做 trim/非空/长度校验）
    // - attachments：附件 URL 列表；Service 会序列化为 JSON 字符串存库

    private String complaintType;

    private String content;

    private List<String> attachments;

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
}
