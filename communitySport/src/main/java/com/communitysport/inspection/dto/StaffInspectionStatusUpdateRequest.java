package com.communitysport.inspection.dto;

public class StaffInspectionStatusUpdateRequest {

    // 巡检上报状态更新请求 DTO（员工端/管理端复用）：
    // - status：目标状态（常见仅允许 SUBMITTED/IN_PROGRESS/RESOLVED）
    // - 合法值与状态流转校验在 Service 中完成

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
