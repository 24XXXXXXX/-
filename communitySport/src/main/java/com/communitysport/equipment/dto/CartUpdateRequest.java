package com.communitysport.equipment.dto;

public class CartUpdateRequest {

    // 购物车更新请求 DTO：
    // - 覆盖式更新（幂等）：同一请求重复提交结果一致
    //
    // 字段语义：
    // - equipmentId：要更新的商品
    // - quantity：目标数量
    //   - quantity <= 0：从购物车移除
    //   - quantity > 0：设置为该数量
    //
    // 具体的合法性校验（例如上限 999、商品必须 ON_SALE）在 Service 层完成。

    private Long equipmentId;

    private Integer quantity;

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
