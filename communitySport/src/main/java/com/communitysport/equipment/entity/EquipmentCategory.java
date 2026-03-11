package com.communitysport.equipment.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("equipment_category")
public class EquipmentCategory {

    // 商品分类表（equipment_category）：
    // - 典型的“字典/枚举”实体
    // - 用于商品目录筛选（categoryId）与展示（categoryName）
    //
    // 本系统在目录接口中按 id 升序输出分类，以保证前端展示顺序稳定。

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // createdAt：分类创建时间
    // - 当前业务不依赖该字段做排序，但可用于后台审计/运营统计

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
