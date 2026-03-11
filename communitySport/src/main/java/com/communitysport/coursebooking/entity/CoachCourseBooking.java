package com.communitysport.coursebooking.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("coach_course_booking")
public class CoachCourseBooking {

    // 课程预约订单表（coach_course_booking）。
    //
    // 这是“课程业务”的核心订单：
    // - 与课次（coach_course_session）关联，代表一次“占座/支付/上课/核销”的完整生命周期
    // - 与钱包流水（wallet_transaction）通过 refType/refId 关联，代表一次真实的资金流
    //
    // 典型状态机（简化）：
    // PENDING_COACH --(教练接单)--> ACCEPTED --(用户支付)--> PAID --(核销)--> USED
    // PENDING_COACH --(教练拒单)--> REJECTED
    // ACCEPTED/PAID --(用户取消)--> CANCELED/REFUNDED
    //
    // 注意：
    // - 状态机是“业务约束”，数据库只存字符串；真正的校验在 service 层完成
    // - amount/status/时间戳字段的组合，决定了退款、入账、核销等动作是否允许执行

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("booking_no")
    private String bookingNo;

    // bookingNo：业务订单号（唯一且更适合对外展示/检索）。
    // - 用于后台查询、对账、定位订单
    // - 在核销时也可以作为“精确定位”的输入

    @TableField("user_id")
    private Long userId;

    // 下单用户（消费者）。

    @TableField("course_session_id")
    private Long courseSessionId;

    // 关联课次（session）。
    // - 占座发生在 session 的 enrolled_count 上
    // - 释放名额也回写 session（取消/拒单时 releaseSeat）

    private Integer amount;

    // 订单金额：与钱包金额单位一致（通常为“分”）。
    // - 由课程价格决定（服务端取 course.price），前端不直接传，避免篡改

    private String status;

    // 订单状态：见类注释中的状态机。

    @TableField("coach_decision_at")
    private LocalDateTime coachDecisionAt;

    // 教练接单/拒单的时间。

    @TableField("reject_reason")
    private String rejectReason;

    // 教练拒单原因（可选），用于用户侧展示。

    @TableField("verification_code")
    private String verificationCode;

    // 核销码：6位随机码，适合线下口述/输入。
    // - 由于随机码理论上可能重复，因此核销逻辑里需要做“歧义处理”（同码多单时拒绝核销）

    @TableField("paid_at")
    private LocalDateTime paidAt;

    // 用户完成支付的时间（ACCEPTED -> PAID）。

    @TableField("used_at")
    private LocalDateTime usedAt;

    // 核销完成时间（PAID -> USED）。
    // - 核销成功后通常伴随“给教练入账”的钱包流水

    @TableField("created_at")
    private LocalDateTime createdAt;

    // 创建时间：用于排序、统计（例如管理员仪表盘的 createdBetween 口径）。

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

    public Long getCourseSessionId() {
        return courseSessionId;
    }

    public void setCourseSessionId(Long courseSessionId) {
        this.courseSessionId = courseSessionId;
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

    public LocalDateTime getCoachDecisionAt() {
        return coachDecisionAt;
    }

    public void setCoachDecisionAt(LocalDateTime coachDecisionAt) {
        this.coachDecisionAt = coachDecisionAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
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
