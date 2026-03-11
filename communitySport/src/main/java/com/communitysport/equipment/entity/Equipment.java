package com.communitysport.equipment.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("equipment")
public class Equipment {

    // 商品主表（equipment）：
    // - 这是“商品中心”的核心实体，承载目录展示、库存、上下架状态等信息
    // - 与订单明细 equipment_order_item 通过 equipment_id 关联
    //
    // 注意：该实体是“写模型/存储模型”，字段设计偏向数据库持久化；
    // 对外展示通常通过 DTO（EquipmentListItem/EquipmentDetailResponse）做裁剪与拼装。

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("category_id")
    private Long categoryId;

    private String name;

    private String spec;

    private String purpose;

    private Integer price;

    private Integer stock;

    // 库存字段（stock）：
    // - 下单时由 EquipmentMapper.subtractStock 以 CAS 条件更新方式扣减（防超卖）
    // - 管理端可直接修改库存（补货/盘点），但用户端只能“消耗库存”（扣减）

    @TableField("cover_url")
    private String coverUrl;

    private String description;

    private String status;

    // 商品状态（status）：
    // - ON_SALE：上架可售（用户端目录/详情可见，下单时也要求 ON_SALE）
    // - OFF_SALE：下架不可售（用户端不可见；即便存在购物车历史行，也会在加购/下单时被拦截）
    //
    // 这里用 String 而非 enum 的取舍：
    // - 数据库存储更直观
    // - 便于未来扩展更多状态（例如 BANNED、DRAFT 等）

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // 时间字段约定：
    // - createdAt：创建时间（由服务端写入）
    // - updatedAt：最近更新时间（每次后台修改商品信息/上下架时刷新）

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
