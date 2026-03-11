package com.communitysport.venue.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 场地实体（venue）。
 *
 * <p>它描述“一个可被用户预约的场所”，例如篮球场、羽毛球场、游泳馆等。
 *
 * <p>字段说明（选取对业务理解最关键的）：
 * <p>- typeId：场地类型（字典表 venue_type），用于前端分类
 * <p>- pricePerHour：小时价（通常为最小单位整数），Timeslot 生成时会根据 slotMinutes 换算
 * <p>- coverUrl：字符串字段，但实际存储的是“图片 URL 列表”的 JSON 数组字符串
 * <p>- status：场地状态（ACTIVE/MAINTENANCE/DISABLED），影响是否允许预约
 * <p>- clickCount：浏览量计数（非严格 UV），用于热门排序/运营统计
 */
@TableName("venue")
public class Venue {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("type_id")
    private Long typeId;

    private String name;

    private String area;

    private String address;

    private String spec;

    @TableField("open_time_desc")
    private String openTimeDesc;

    @TableField("price_per_hour")
    private Integer pricePerHour;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("cover_url")
    private String coverUrl;

    private String description;

    private String status;

    @TableField("click_count")
    private Integer clickCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getOpenTimeDesc() {
        return openTimeDesc;
    }

    public void setOpenTimeDesc(String openTimeDesc) {
        this.openTimeDesc = openTimeDesc;
    }

    public Integer getPricePerHour() {
        return pricePerHour;
    }

    public void setPricePerHour(Integer pricePerHour) {
        this.pricePerHour = pricePerHour;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
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

    public Integer getClickCount() {
        return clickCount;
    }

    public void setClickCount(Integer clickCount) {
        this.clickCount = clickCount;
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
