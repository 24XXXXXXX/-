package com.communitysport.equipment.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("equipment_cart_item")
public class EquipmentCartItem {

    // 购物车行（equipment_cart_item）：
    // - 属于“用户临时意向”的写模型：用户想买什么 + 数量
    // - 下单时会读取这些行生成订单，然后清空购物车
    //
    // 业务约束（通常由数据库唯一索引保证）：
    // - 同一 user_id + equipment_id 只允许存在一行
    // - 并发下 Service 会用 DuplicateKeyException 做补偿，保证最终幂等

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("equipment_id")
    private Long equipmentId;

    private Integer quantity;

    // quantity：购物车中的数量
    // - updateCart 采用“覆盖式更新”，所以 quantity 是最终状态，而不是增量

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // 时间字段约定：
    // - createdAt：首次加入购物车时间
    // - updatedAt：最近一次修改数量时间

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
