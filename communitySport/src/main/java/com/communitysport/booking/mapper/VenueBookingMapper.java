package com.communitysport.booking.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.booking.entity.VenueBooking;

/**
 * 场地预约订单 Mapper。
 *
 * <p>大多数 CRUD 由 MyBatis-Plus 的 BaseMapper 提供。
 * <p>这里额外提供两个聚合统计：
 * <p>- countCreatedBetween：某时间区间新建订单数（用于管理员仪表盘）
 * <p>- countUsedBetween：某时间区间已使用订单数（用于管理员仪表盘）
 */
@Mapper
public interface VenueBookingMapper extends BaseMapper<VenueBooking> {

    @Select("SELECT COUNT(1) FROM venue_booking WHERE created_at >= #{start} AND created_at < #{end}")
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(1) FROM venue_booking WHERE used_at IS NOT NULL AND used_at >= #{start} AND used_at < #{end}")
    long countUsedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
