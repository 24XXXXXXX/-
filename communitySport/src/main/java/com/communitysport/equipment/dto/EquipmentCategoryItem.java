package com.communitysport.equipment.dto;

public class EquipmentCategoryItem {

    // 分类列表项 DTO：
    // - 用于 /api/equipment/categories 接口返回
    // - 作为前端筛选条件（下拉框/tab）使用，因此只需要 id + name

    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
