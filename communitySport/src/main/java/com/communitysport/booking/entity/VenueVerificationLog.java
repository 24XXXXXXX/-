package com.communitysport.booking.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 场地订单核销日志（venue_verification_log）。
 *
 * <p>核销是“线下履约确认”的关键审计点：
 * <p>- 谁（staffUserId）
 * <p>- 在何时（verifiedAt）
 * <p>- 对哪笔订单（bookingId）
 * <p>- 做了什么结果（result/remark）
 *
 * <p>即使订单表里有 usedAt，我们仍保留日志表：
 * <p>- 可以记录多次尝试（成功/失败）
 * <p>- 可以追责与统计（哪个员工核销了多少单）
 */
@TableName("venue_verification_log")
public class VenueVerificationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("booking_id")
    private Long bookingId;

    @TableField("staff_user_id")
    private Long staffUserId;

    @TableField("verified_at")
    private LocalDateTime verifiedAt;

    private String result;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getStaffUserId() {
        return staffUserId;
    }

    public void setStaffUserId(Long staffUserId) {
        this.staffUserId = staffUserId;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
