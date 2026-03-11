package com.communitysport.equipment.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("equipment_order")
public class EquipmentOrder {

    // 商品订单主表（equipment_order）：
    // - 对应用户一次“下单”行为的主记录（谁买的、买了多少、收货信息、状态、时间点等）
    // - 与订单明细 equipment_order_item 是 1:N 关系
    //
    // 该表在本系统里同时承担：
    // - 交易/履约状态机的载体（CREATED/PAID/SHIPPED/RECEIVED）
    // - 管理端统计的事实表（created_at / paid_at 时间窗统计）

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    // 订单号（orderNo）：
    // - 业务侧可展示/可查询的编号
    // - 由服务端生成（当前用 UUID 去横线），避免前端伪造

    @TableField("user_id")
    private Long userId;

    @TableField("total_amount")
    private Integer totalAmount;

    private String status;

    // 订单状态（status）：
    // - CREATED：订单已创建（本系统下单流程会很快扣款并进入 PAID）
    // - PAID：已支付，待发货
    // - SHIPPED：已发货，待收货
    // - RECEIVED：已收货
    //
    // 注意：这里未使用 enum，原因与其他模块类似：
    // - 数据库存储直观
    // - 易于扩展更多状态（例如 CANCELED/REFUNDED 等）

    @TableField("receiver_name")
    private String receiverName;

    @TableField("receiver_phone")
    private String receiverPhone;

    @TableField("receiver_address")
    private String receiverAddress;

    // 收货信息三要素：
    // - 下单必填，用于履约发货
    // - 本系统未做地址簿/多地址管理，因此直接存到订单上（作为快照）

    @TableField("logistics_company")
    private String logisticsCompany;

    @TableField("tracking_no")
    private String trackingNo;

    // 物流字段：
    // - 由管理员/员工在发货时填写
    // - 允许对已 SHIPPED 的订单更新（录错更正），shippedAt 仅记录首次发货时间

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("shipped_at")
    private LocalDateTime shippedAt;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // 时间点字段约定：
    // - createdAt：创建订单时间（统计“下单量”时使用）
    // - paidAt：支付完成时间（统计“成交额”时使用；paid_at 非空才计入成交）
    // - shippedAt：首次发货时间
    // - receivedAt：用户确认收货时间

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

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

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
