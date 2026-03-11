package com.communitysport.coursebooking.dto;

public class CourseBookingCreateRequest {

    private Long sessionId;

    // 用户发起课程预约时，只需要告诉后端“选择了哪个课次（session）”。
    //
    // 重要原则：
    // - 金额 amount 不由前端传入，而是由后端根据 session -> course -> price 计算/读取
    // - 防止前端篡改价格
    //
    // 并发/幂等提示：
    // - 后端会对同一 user+session 的“未结束订单”做去重（Already booked），避免重复占座

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
