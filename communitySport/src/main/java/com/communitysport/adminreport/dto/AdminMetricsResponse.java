package com.communitysport.adminreport.dto;

import java.time.LocalDate;

/**
 * 管理员仪表盘指标响应 DTO。
 *
 * <p>它是一组“聚合指标”，用于 Dashboard 展示，不是明细列表。
 * <p>注意：金额字段（totalPayAmount/totalRefundAmount/totalTopupAmount/signinRewardAmount）
 * 通常是“最小单位整数”（例如分/积分），前端展示时再做单位换算或格式化。
 */
public class AdminMetricsResponse {

    // startDate/endDate：本次指标统计的日期区间（均包含）
    private LocalDate startDate;

    private LocalDate endDate;

    private Long newUsers;

    // newUsers：区间内新注册/新创建的用户数

    private Long activeUsers;

    // activeUsers：区间内活跃用户数（通常按 lastLoginAt 落在区间内统计）

    private Long totalPayAmount;

    // totalPayAmount：区间内支付总额（最小单位整数，例如分/积分）

    private Long totalRefundAmount;

    // totalRefundAmount：区间内退款入账总额（最小单位整数）

    private Long totalTopupAmount;

    // totalTopupAmount：区间内充值入账总额（最小单位整数）

    private Long signinRewardAmount;

    // signinRewardAmount：区间内签到奖励发放总额（最小单位整数）

    private Long venueTimeslotTotal;

    // venueTimeslotTotal：区间内场地可用时段总数

    private Long venueTimeslotBooked;

    // venueTimeslotBooked：区间内场地已被预订时段数

    private Double venueUtilizationRate;

    // venueUtilizationRate：场地利用率（booked / total），用于展示

    private Long venueBookingsCreated;

    // venueBookingsCreated：区间内新建的场地预约订单数

    private Long venueBookingsUsed;

    // venueBookingsUsed：区间内已核销/已使用的场地预约订单数

    private Long courseBookingsCreated;

    private Long courseBookingsUsed;

    private Long equipmentOrdersCreated;

    private Long equipmentOrdersPaidAmount;

    private Long videoPurchasesCreated;

    private Long complaintsCreated;

    // complaintsCreated：区间内新建投诉数量

    private Long complaintsResolved;

    // complaintsResolved：区间内已解决投诉数量（通常按 resolvedAt 落在区间内统计）

    private Double complaintResolutionRate;

    // complaintResolutionRate：投诉解决率（resolved / created），用于展示

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Long getNewUsers() {
        return newUsers;
    }

    public void setNewUsers(Long newUsers) {
        this.newUsers = newUsers;
    }

    public Long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Long getTotalPayAmount() {
        return totalPayAmount;
    }

    public void setTotalPayAmount(Long totalPayAmount) {
        this.totalPayAmount = totalPayAmount;
    }

    public Long getTotalRefundAmount() {
        return totalRefundAmount;
    }

    public void setTotalRefundAmount(Long totalRefundAmount) {
        this.totalRefundAmount = totalRefundAmount;
    }

    public Long getTotalTopupAmount() {
        return totalTopupAmount;
    }

    public void setTotalTopupAmount(Long totalTopupAmount) {
        this.totalTopupAmount = totalTopupAmount;
    }

    public Long getSigninRewardAmount() {
        return signinRewardAmount;
    }

    public void setSigninRewardAmount(Long signinRewardAmount) {
        this.signinRewardAmount = signinRewardAmount;
    }

    public Long getVenueTimeslotTotal() {
        return venueTimeslotTotal;
    }

    public void setVenueTimeslotTotal(Long venueTimeslotTotal) {
        this.venueTimeslotTotal = venueTimeslotTotal;
    }

    public Long getVenueTimeslotBooked() {
        return venueTimeslotBooked;
    }

    public void setVenueTimeslotBooked(Long venueTimeslotBooked) {
        this.venueTimeslotBooked = venueTimeslotBooked;
    }

    public Double getVenueUtilizationRate() {
        return venueUtilizationRate;
    }

    public void setVenueUtilizationRate(Double venueUtilizationRate) {
        this.venueUtilizationRate = venueUtilizationRate;
    }

    public Long getVenueBookingsCreated() {
        return venueBookingsCreated;
    }

    public void setVenueBookingsCreated(Long venueBookingsCreated) {
        this.venueBookingsCreated = venueBookingsCreated;
    }

    public Long getVenueBookingsUsed() {
        return venueBookingsUsed;
    }

    public void setVenueBookingsUsed(Long venueBookingsUsed) {
        this.venueBookingsUsed = venueBookingsUsed;
    }

    public Long getCourseBookingsCreated() {
        return courseBookingsCreated;
    }

    public void setCourseBookingsCreated(Long courseBookingsCreated) {
        this.courseBookingsCreated = courseBookingsCreated;
    }

    public Long getCourseBookingsUsed() {
        return courseBookingsUsed;
    }

    public void setCourseBookingsUsed(Long courseBookingsUsed) {
        this.courseBookingsUsed = courseBookingsUsed;
    }

    public Long getEquipmentOrdersCreated() {
        return equipmentOrdersCreated;
    }

    public void setEquipmentOrdersCreated(Long equipmentOrdersCreated) {
        this.equipmentOrdersCreated = equipmentOrdersCreated;
    }

    public Long getEquipmentOrdersPaidAmount() {
        return equipmentOrdersPaidAmount;
    }

    public void setEquipmentOrdersPaidAmount(Long equipmentOrdersPaidAmount) {
        this.equipmentOrdersPaidAmount = equipmentOrdersPaidAmount;
    }

    public Long getVideoPurchasesCreated() {
        return videoPurchasesCreated;
    }

    public void setVideoPurchasesCreated(Long videoPurchasesCreated) {
        this.videoPurchasesCreated = videoPurchasesCreated;
    }

    public Long getComplaintsCreated() {
        return complaintsCreated;
    }

    public void setComplaintsCreated(Long complaintsCreated) {
        this.complaintsCreated = complaintsCreated;
    }

    public Long getComplaintsResolved() {
        return complaintsResolved;
    }

    public void setComplaintsResolved(Long complaintsResolved) {
        this.complaintsResolved = complaintsResolved;
    }

    public Double getComplaintResolutionRate() {
        return complaintResolutionRate;
    }

    public void setComplaintResolutionRate(Double complaintResolutionRate) {
        this.complaintResolutionRate = complaintResolutionRate;
    }
}
