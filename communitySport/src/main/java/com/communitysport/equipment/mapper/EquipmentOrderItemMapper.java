package com.communitysport.equipment.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.equipment.entity.EquipmentOrderItem;

@Mapper
public interface EquipmentOrderItemMapper extends BaseMapper<EquipmentOrderItem> {
    // 订单明细表 Mapper：
    // - equipment_order_item 与 equipment_order 是典型的 1:N 关系
    // - 本系统里通常通过 order_id 来查询某个订单的明细行，用于组装“订单详情” DTO
}
