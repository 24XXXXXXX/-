package com.communitysport.inspection.dto;

import java.time.LocalDate;

public class StaffDailyReportResponse {

    // 员工个人日报响应 DTO（聚合读模型）：
    // - 该接口不返回明细列表，而是返回“当天工作量/指标”的汇总
    // - Service 侧通常以半开区间 [startOfDay, nextDayStart) 统计，避免边界重复
    //
    // 字段说明：
    // - venuesTotal/venuesMaintenance/venuesDisabled：员工所在片区的场地总量及状态分布
    // - myVenueVerifications：当天本人完成的场地核销次数
    // - myComplaintsUpdated/myComplaintsResolved：当天本人处理的投诉更新/解决数量
    // - myInspectionReports：当天本人提交的巡检上报数量
    // - equipmentLowStock：全局低库存器材数量（阈值由 Service 常量控制）

    private LocalDate date;

    private Long staffUserId;

    private String region;

    private Long venuesTotal;

    private Long venuesMaintenance;

    private Long venuesDisabled;

    private Long myVenueVerifications;

    private Long myComplaintsUpdated;

    // myComplaintsUpdated：当天“被我处理过”的投诉数量
    // - 统计口径通常按 updatedAt 落在当天区间内

    private Long myComplaintsResolved;

    // myComplaintsResolved：当天“被我解决”的投诉数量
    // - 统计口径通常按 resolvedAt 落在当天区间内

    private Long myInspectionReports;

    private Long equipmentLowStock;

    // equipmentLowStock：低库存器材数量
    // - 注意：此处通常是全局口径（不按片区），阈值由 Service 常量控制

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getStaffUserId() {
        return staffUserId;
    }

    public void setStaffUserId(Long staffUserId) {
        this.staffUserId = staffUserId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Long getVenuesTotal() {
        return venuesTotal;
    }

    public void setVenuesTotal(Long venuesTotal) {
        this.venuesTotal = venuesTotal;
    }

    public Long getVenuesMaintenance() {
        return venuesMaintenance;
    }

    public void setVenuesMaintenance(Long venuesMaintenance) {
        this.venuesMaintenance = venuesMaintenance;
    }

    public Long getVenuesDisabled() {
        return venuesDisabled;
    }

    public void setVenuesDisabled(Long venuesDisabled) {
        this.venuesDisabled = venuesDisabled;
    }

    public Long getMyVenueVerifications() {
        return myVenueVerifications;
    }

    public void setMyVenueVerifications(Long myVenueVerifications) {
        this.myVenueVerifications = myVenueVerifications;
    }

    public Long getMyComplaintsUpdated() {
        return myComplaintsUpdated;
    }

    public void setMyComplaintsUpdated(Long myComplaintsUpdated) {
        this.myComplaintsUpdated = myComplaintsUpdated;
    }

    public Long getMyComplaintsResolved() {
        return myComplaintsResolved;
    }

    public void setMyComplaintsResolved(Long myComplaintsResolved) {
        this.myComplaintsResolved = myComplaintsResolved;
    }

    public Long getMyInspectionReports() {
        return myInspectionReports;
    }

    public void setMyInspectionReports(Long myInspectionReports) {
        this.myInspectionReports = myInspectionReports;
    }

    public Long getEquipmentLowStock() {
        return equipmentLowStock;
    }

    public void setEquipmentLowStock(Long equipmentLowStock) {
        this.equipmentLowStock = equipmentLowStock;
    }
}
