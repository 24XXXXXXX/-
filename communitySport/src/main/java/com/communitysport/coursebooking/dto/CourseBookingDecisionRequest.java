package com.communitysport.coursebooking.dto;

public class CourseBookingDecisionRequest {

    private String rejectReason;

    // 教练端拒单原因（可选）。
    // - 用于向用户解释为什么拒绝接单
    // - service/controller 通常会对其 trim，并在为空时忽略

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }
}
