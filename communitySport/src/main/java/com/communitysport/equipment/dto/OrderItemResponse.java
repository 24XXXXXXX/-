package com.communitysport.equipment.dto;

public class OrderItemResponse {

    // 订单明细项响应 DTO（读模型）：
    // - 来自 equipment_order_item 的快照字段
    // - 主要用于订单详情页展示与对账

    private Long equipmentId;

    private String equipmentName;

    private Integer price;

    private Integer quantity;

    private Integer subtotal;

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(Integer subtotal) {
        this.subtotal = subtotal;
    }
}
