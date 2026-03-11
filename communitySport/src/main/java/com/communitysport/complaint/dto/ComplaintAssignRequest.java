package com.communitysport.complaint.dto;

public class ComplaintAssignRequest {

    // 指派处理人请求 DTO（管理端）：
    // - staffUserId：被指派的员工/管理员用户 id
    // - 是否允许指派、以及指派后状态是 ASSIGNED 还是 IN_PROGRESS 由 Service 根据当前状态决定

    private Long staffUserId;

    public Long getStaffUserId() {
        return staffUserId;
    }

    public void setStaffUserId(Long staffUserId) {
        this.staffUserId = staffUserId;
    }
}
