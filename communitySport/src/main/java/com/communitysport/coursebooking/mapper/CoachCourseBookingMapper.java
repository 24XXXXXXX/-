package com.communitysport.coursebooking.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.coursebooking.entity.CoachCourseBooking;

/**
 * 教练端订单Mapper
 */
@Mapper
public interface CoachCourseBookingMapper extends BaseMapper<CoachCourseBooking> {

    // 教练端订单列表统计（分页前先 count）：
    // - 通过 b(订单) -> s(课次) -> c(课程) 的 JOIN，把“资源归属”下推到 SQL 层保证数据隔离
    // - 只要 c.coach_user_id = 当前教练，就能确保教练只能看到/统计自己的课程订单
    //
    // (#{param} IS NULL OR #{param} = '' OR column = #{param}) 的写法是“可选过滤条件”模板：
    // - 不传/空字符串：不加该过滤
    // - 传值：精确匹配
    // 这种写法简单直观，但要注意它会让 SQL 无法完全走等值索引的最优形态（属于可接受的工程权衡）。
    @Select("SELECT COUNT(1) FROM coach_course_booking b "
        + "JOIN coach_course_session s ON s.id = b.course_session_id "
        + "JOIN coach_course c ON c.id = s.course_id "
        + "WHERE c.coach_user_id = #{coachUserId} "
        + "AND (#{status} IS NULL OR #{status} = '' OR b.status = #{status}) "
        + "AND (#{bookingNo} IS NULL OR #{bookingNo} = '' OR b.booking_no = #{bookingNo})")
    long countCoachBookings(
            @Param("coachUserId") Long coachUserId,
            @Param("status") String status,
            @Param("bookingNo") String bookingNo
    );

    // 教练端订单分页查询：
    // - 同样使用 JOIN 做教练归属约束（防止越权读取）
    // - ORDER BY b.id DESC：按最新订单优先
    // - LIMIT/OFFSET：典型的“偏移分页”方式，对应前端的 page/size
    //   - offset = (page - 1) * size
    //   - limit = size
    @Select("SELECT b.id, b.booking_no, b.user_id, b.course_session_id, b.amount, b.status, b.coach_decision_at, "
        + "b.reject_reason, b.verification_code, b.paid_at, b.used_at, b.created_at "
        + "FROM coach_course_booking b "
        + "JOIN coach_course_session s ON s.id = b.course_session_id "
        + "JOIN coach_course c ON c.id = s.course_id "
        + "WHERE c.coach_user_id = #{coachUserId} "
        + "AND (#{status} IS NULL OR #{status} = '' OR b.status = #{status}) "
        + "AND (#{bookingNo} IS NULL OR #{bookingNo} = '' OR b.booking_no = #{bookingNo}) "
        + "ORDER BY b.id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<CoachCourseBooking> selectCoachBookingsPage(
            @Param("coachUserId") Long coachUserId,
            @Param("status") String status,
            @Param("bookingNo") String bookingNo,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    // 报表统计：统计某个时间段内创建的订单数。
    // - 使用 [start, end) 半开区间，避免“end 时刻是否包含”的歧义
    // - 常用于按天/按月统计：end 一般取下一天/下一月的起点
    @Select("SELECT COUNT(1) FROM coach_course_booking WHERE created_at >= #{start} AND created_at < #{end}")
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 报表统计：统计某个时间段内已核销（used_at 非空）的订单数。
    // used_at 的语义通常是“核销完成时间”，与 created_at/paid_at 是不同维度指标。
    @Select("SELECT COUNT(1) FROM coach_course_booking WHERE used_at IS NOT NULL AND used_at >= #{start} AND used_at < #{end}")
    long countUsedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
