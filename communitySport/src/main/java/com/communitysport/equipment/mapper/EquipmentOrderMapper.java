package com.communitysport.equipment.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.equipment.entity.EquipmentOrder;

@Mapper
public interface EquipmentOrderMapper extends BaseMapper<EquipmentOrder> {

    // 统计口径：按创建时间统计某个时间窗内创建的订单数。
    //
    // 时间区间采用半开区间 [start, end)：
    // - start <= created_at < end
    // - 便于把一天/一小时等连续区间无缝拼接，避免边界重复统计
    @Select("SELECT COUNT(1) FROM equipment_order WHERE created_at >= #{start} AND created_at < #{end}")
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 统计口径：按支付时间统计某个时间窗内“已支付订单”的成交金额。
    //
    // 关键点：
    // - paid_at IS NOT NULL：只统计真正完成支付的订单（避免把 CREATED 等未支付状态算进去）
    // - paid_at 也采用 [start, end) 半开区间
    // - COALESCE(SUM(...), 0)：当没有任何匹配行时，SUM 会返回 NULL，这里把 NULL 转成 0，便于上层直接展示/计算
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM equipment_order WHERE paid_at IS NOT NULL AND paid_at >= #{start} AND paid_at < #{end}")
    long sumPaidAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
