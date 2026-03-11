package com.communitysport.equipment.dto;

import java.time.LocalDateTime;

public class EquipmentDetailResponse {

    // 商品详情响应 DTO（读模型）：
    // - 面向前端“详情页”展示
    // - 字段来源主要是 equipment 表 + categoryName（来自 equipment_category）
    // - 不直接暴露数据库实体，避免把内部字段/约束透传到接口层

    private Long id;

    private Long categoryId;

    private String categoryName;

    // categoryName：分类名称
    // - 这是为了减少前端额外请求（避免前端拿 categoryId 再查一次分类字典）

    private String name;

    private String spec;

    private String purpose;

    private Integer price;

    private Integer stock;

    // stock：当前库存
    // - 用户端主要用于“是否可买/可加购”的展示
    // - 真正的防超卖由下单时的库存 CAS 扣减保证

    private String coverUrl;

    private String description;

    private String status;

    // status：商品状态（ON_SALE/OFF_SALE）
    // - 用户端接口通常只会返回 ON_SALE；后台接口可能返回所有状态

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // createdAt/updatedAt：用于后台审计与前端展示（例如“上新时间/最近编辑时间”）

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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
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
