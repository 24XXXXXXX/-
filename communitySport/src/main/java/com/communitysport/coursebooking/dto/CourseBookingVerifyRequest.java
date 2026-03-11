package com.communitysport.coursebooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CourseBookingVerifyRequest {

    private String bookingNo;

    // 优先推荐传 bookingNo：业务唯一，可精确定位订单。
    // - 避免仅靠核销码导致的“同码歧义”问题

    @JsonAlias("verifyCode")
    private String verificationCode;

    // 核销码（6位随机码）。
    //
    // JsonAlias("verifyCode") 用于兼容前端字段命名：
    // - 有的前端页面/组件可能使用 verifyCode 作为字段名
    // - 后端统一映射到 verificationCode，避免因字段名不一致造成 400

    public String getBookingNo() {
        return bookingNo;
    }

    public void setBookingNo(String bookingNo) {
        this.bookingNo = bookingNo;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }
}
