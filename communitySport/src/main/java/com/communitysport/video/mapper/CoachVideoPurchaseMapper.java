package com.communitysport.video.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.video.entity.CoachVideoPurchase;

@Mapper
public interface CoachVideoPurchaseMapper extends BaseMapper<CoachVideoPurchase> {

    // 统计：某个时间区间内“已支付”的购买单数量
    // - 采用半开区间 [start, end) 以避免边界重复统计
    // - status = 'PAID' 表示该购买记录已完成扣款（即有效购买）
    // - 典型用途：后台报表/教练收益统计的订单数口径

    @Select("SELECT COUNT(1) FROM coach_video_purchase WHERE status = 'PAID' AND created_at >= #{start} AND created_at < #{end}")
    long countPaidCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
