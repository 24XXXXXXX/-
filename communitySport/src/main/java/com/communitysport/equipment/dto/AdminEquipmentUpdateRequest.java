package com.communitysport.equipment.dto;

public class AdminEquipmentUpdateRequest {

    // 后台编辑商品请求 DTO：
    // - 采用“部分更新（PATCH 风格）”的载体：字段为 null 表示不修改该字段
    // - 具体的字段合法性校验、trim、以及 updatedAt 刷新等都在 Service 层完成

    private Long categoryId;

    private String name;

    private String spec;

    private String purpose;

    private Integer price;

    private Integer stock;

    private String coverUrl;

    private String description;

    private String status;

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
}
