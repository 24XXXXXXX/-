package com.communitysport.equipment.dto;

public class OrderShipRequest {

    // 发货请求 DTO（管理端/员工端）：
    // - 填写物流公司与运单号
    // - 本系统允许对已 SHIPPED 的订单更新物流信息（录错更正）
    // - 字段必填校验在 Service 层完成

    private String logisticsCompany;

    private String trackingNo;

    public String getLogisticsCompany() {
        return logisticsCompany;
    }

    public void setLogisticsCompany(String logisticsCompany) {
        this.logisticsCompany = logisticsCompany;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }
}
