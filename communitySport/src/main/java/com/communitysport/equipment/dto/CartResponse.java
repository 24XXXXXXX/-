package com.communitysport.equipment.dto;

import java.util.List;

public class CartResponse {

    // 购物车响应 DTO（读模型）：
    // - items：购物车行列表（每行包含商品信息与数量）
    // - totalQuantity/totalAmount：服务端汇总字段，便于前端直接展示“件数/合计”
    //
    // 注意：totalAmount 由服务端根据商品单价 * 数量计算得到，避免前端篡改金额。

    private List<CartItemResponse> items;

    private Integer totalQuantity;

    private Integer totalAmount;

    public List<CartItemResponse> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponse> items) {
        this.items = items;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
    }
}
