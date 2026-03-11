package com.communitysport.complaint.dto;

public class ComplaintStatusUpdateRequest {

    // 工单状态更新请求 DTO（员工端/管理端）：
    // - 目标状态通常仅允许 IN_PROGRESS / RESOLVED
    // - 状态机合法流转（例如已 RESOLVED 是否允许再次改动）在 Service 中校验

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
