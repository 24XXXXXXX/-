package com.communitysport.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("equipment_order_item")
public class EquipmentOrderItem {

    // 商品订单明细表（equipment_order_item）：
    // - 记录某个订单里“每一件商品”的购买快照（商品、单价、数量、小计）
    // - 与 equipment_order 通过 order_id 关联，形成 1:N
    //
    // 为什么要在明细里存 equipmentName/price/subtotal，而不是每次都 join equipment？
    // - 订单属于“历史事实”，需要稳定可追溯
    // - 商品可能被后台改名、改价、下架甚至删除
    // - 存快照可避免历史订单展示被商品变更影响，也便于对账

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    // orderId：指向订单主表 equipment_order.id
    // - 订单详情查询时通常先查主表，再按 orderId 批量拉取明细列表

    @TableField("equipment_id")
    private Long equipmentId;

    @TableField("equipment_name")
    private String equipmentName;

    // equipmentName：商品名称快照

    private Integer price;

    // price：下单时的单价快照

    private Integer quantity;

    private Integer subtotal;

    // subtotal：price * quantity 的计算结果快照
    // - 冗余字段可以减少展示时的重复计算
    // - 也便于审计/对账（直接对比小计与总价）

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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
