package com.communitysport.equipment.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.equipment.entity.EquipmentCartItem;

@Mapper
public interface EquipmentCartItemMapper extends BaseMapper<EquipmentCartItem> {
    // 购物车行 Mapper：
    // - 每一行表示某个用户对某个商品的“数量”
    // - 业务上通常期望 (user_id, equipment_id) 唯一（并发下由唯一索引 + Service 层补偿处理保证幂等）
}
