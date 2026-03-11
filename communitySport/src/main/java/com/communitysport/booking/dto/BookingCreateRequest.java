package com.communitysport.booking.dto;

/**
 * 创建场地预约订单请求。
 *
 * <p>设计成“只传 timeslotId”：
 * <p>- 金额 price 来自后端的 venue_timeslot.price（防篡改）
 * <p>- 场地/时间信息也从 timeslot 反查（避免前端传入导致不一致）
 */
public class BookingCreateRequest {

    private Long timeslotId;

    public Long getTimeslotId() {
        return timeslotId;
    }

    public void setTimeslotId(Long timeslotId) {
        this.timeslotId = timeslotId;
    }
}
