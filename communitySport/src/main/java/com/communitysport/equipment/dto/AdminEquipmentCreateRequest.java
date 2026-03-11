package com.communitysport.equipment.dto;

public class AdminEquipmentCreateRequest {

    // 后台新增商品请求 DTO：
    // - 由管理员在管理端提交
    // - 仅承载“客户端输入”，不包含任何服务端计算字段
    //
    // 参数校验与默认值策略在 Service 层统一处理，例如：
    // - categoryId/name 必填
    // - price/stock 非负
    // - status 为空时默认 ON_SALE

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
