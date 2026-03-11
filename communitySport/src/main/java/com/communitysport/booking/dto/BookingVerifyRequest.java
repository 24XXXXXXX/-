package com.communitysport.booking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 场地订单核销请求。
 *
 * <p>支持两种定位方式：
 * <p>- bookingNo：订单号（精确、不歧义）
 * <p>- verificationCode：核销码（6 位，可能极小概率撞码，因此后端会做歧义判断）
 *
 * <p>@JsonAlias("verifyCode")：兼容前端可能使用的字段名。
 */
public class BookingVerifyRequest {

    private String bookingNo;

    @JsonAlias("verifyCode")
    private String verificationCode;

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
