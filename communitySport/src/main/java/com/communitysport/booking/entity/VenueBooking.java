package com.communitysport.booking.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 场地预约订单实体（venue_booking）。
 *
 * <p>它描述的是“用户预约了哪个场地的哪个时段，并支付了多少金额”。
 *
 * <p>关键字段说明：
 * <p>- bookingNo：对外可展示/检索的订单号（与自增 id 分离）
 * <p>- timeslotId：绑定的可预约时段（venue_timeslot），用于确定开始/结束时间
 * <p>- amount：订单金额（通常为最小单位整数）
 * <p>- status：订单业务状态（本项目常见：CREATED/PAID/CANCELED/REFUNDED/USED）
 * <p>- verificationCode：核销码（用于现场核销）
 * <p>- paidAt/canceledAt/usedAt：关键业务时间点，用于统计与风控
 */
@TableName("venue_booking")
public class VenueBooking {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("booking_no")
    private String bookingNo;

    @TableField("user_id")
    private Long userId;

    @TableField("venue_id")
    private Long venueId;

    @TableField("timeslot_id")
    private Long timeslotId;

    private Integer amount;

    private String status;

    @TableField("verification_code")
    private String verificationCode;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("canceled_at")
    private LocalDateTime canceledAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookingNo() {
        return bookingNo;
    }

    public void setBookingNo(String bookingNo) {
        this.bookingNo = bookingNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public Long getTimeslotId() {
        return timeslotId;
    }

    public void setTimeslotId(Long timeslotId) {
        this.timeslotId = timeslotId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public void setCanceledAt(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
