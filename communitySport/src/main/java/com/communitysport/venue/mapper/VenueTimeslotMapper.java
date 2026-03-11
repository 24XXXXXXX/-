package com.communitysport.venue.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.venue.entity.VenueTimeslot;

/**
 * 场地可预约时段（venue_timeslot）Mapper。
 *
 * <p>重点方法：updateStatusIfMatch
 * <p>它是一个典型的“条件更新（CAS：Compare-And-Set）”写法：
 * <p>- UPDATE ... SET status=toStatus WHERE id=? AND status=fromStatus
 *
 * <p>为什么非常重要？
 * <p>- 并发抢占：两个用户同时下单同一时段时，只有一个请求能把 AVAILABLE 改成 BOOKED
 * <p>- 幂等释放：取消/退款释放时段时，也只能把 BOOKED 改回 AVAILABLE（不会误改其他状态）
 *
 * <p>返回值语义：
 * <p>- 1：更新成功（状态确实从 fromStatus 变为 toStatus）
 * <p>- 0：更新失败（状态不匹配/记录不存在），上层通常转成 409/忽略
 */
@Mapper
public interface VenueTimeslotMapper extends BaseMapper<VenueTimeslot> {

    @Update("UPDATE venue_timeslot SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
    int updateStatusIfMatch(@Param("id") Long id, @Param("toStatus") String toStatus, @Param("fromStatus") String fromStatus);

    // 下面两个 count 用于管理员仪表盘的“场地利用率”计算：
    // - venueTimeslotTotal：区间内一共生成了多少可预约时段
    // - venueTimeslotBooked：其中有多少被预约（status=BOOKED）
    @Select("SELECT COUNT(1) FROM venue_timeslot WHERE start_time >= #{start} AND start_time < #{end}")
    long countByStartTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(1) FROM venue_timeslot WHERE start_time >= #{start} AND start_time < #{end} AND status = 'BOOKED'")
    long countBookedByStartTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
