package com.communitysport.complaint.dto;

import java.util.List;

public class ComplaintMessageCreateRequest {

    // 工单消息新增请求 DTO（写模型）：
    // - 用于用户/员工/管理员在某条投诉下追加沟通消息
    // - attachments 通常是附件 URL 列表（在 Service 中会序列化为 JSON 字符串存库）
    // - content 长度/空白校验在 Service 中统一处理

    private String content;

    private List<String> attachments;

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
