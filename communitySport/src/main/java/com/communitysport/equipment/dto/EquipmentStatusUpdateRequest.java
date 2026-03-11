package com.communitysport.equipment.dto;

public class EquipmentStatusUpdateRequest {

    // 商品状态更新请求 DTO（后台上/下架）：
    // - 该 DTO 只承载目标状态，不允许前端在同一接口里修改其他字段
    // - status 合法性（仅 ON_SALE / OFF_SALE）在 Service 层校验

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
