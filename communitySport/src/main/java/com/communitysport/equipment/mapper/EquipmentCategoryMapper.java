package com.communitysport.equipment.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.equipment.entity.EquipmentCategory;

@Mapper
public interface EquipmentCategoryMapper extends BaseMapper<EquipmentCategory> {
    // 商品分类 Mapper：
    // - equipment_category 可视为“字典/枚举表”，用于目录筛选与展示
    // - 读多写少，因此大多数场景直接使用 BaseMapper 的基础 CRUD 即可
}
