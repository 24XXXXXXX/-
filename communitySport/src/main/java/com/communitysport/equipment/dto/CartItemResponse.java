package com.communitysport.equipment.dto;

public class CartItemResponse {

    // 购物车行响应 DTO（读模型）：
    // - 由 equipment_cart_item（数量） + equipment（商品展示信息）拼装得到
    // - subtotal 为冗余字段，便于前端直接展示小计（price * quantity）

    private Long id;

    private Long equipmentId;

    private String equipmentName;

    private String coverUrl;

    private Integer price;

    private Integer quantity;

    private Integer subtotal;

    // subtotal：单行小计
    // - 由服务端计算得到，避免前端重复计算/因精度或空值处理导致不一致

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
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
