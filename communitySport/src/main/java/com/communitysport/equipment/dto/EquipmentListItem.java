package com.communitysport.equipment.dto;

public class EquipmentListItem {

    // 商品列表项 DTO（读模型）：
    // - 用于目录列表/后台列表等“多条数据”场景
    // - 相比详情 DTO，它只保留列表页需要的字段，以减少 payload
    // - 不包含 description（长文本），避免列表接口传输过大

    private Long id;

    private Long categoryId;

    private String categoryName;

    // categoryName：分类名称（服务端拼好，减少前端额外查字典的请求）

    private String name;

    private String spec;

    private String purpose;

    private Integer price;

    private Integer stock;

    private String coverUrl;

    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
