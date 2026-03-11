package com.communitysport.course.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.course.entity.CoachCourseSession;

@Mapper
public interface CoachCourseSessionMapper extends BaseMapper<CoachCourseSession> {

    // 课次占座（并发控制核心点）：
    // - enrolled_count 表示“已占用名额数”（报名成功但可能尚未支付/核销）
    // - 通过单条 UPDATE + 条件判断完成“检查 + 修改”的原子性，避免并发超卖
    // - 返回值为受影响行数：
    //   - 1 表示占座成功
    //   - 0 表示占座失败（可能已满员、课次非 OPEN、或 id 不存在）
    // 这种模式本质是 CAS（Compare-And-Set）/乐观锁的数据库实现：把“是否还能占座”的判断放进 WHERE 条件里。
    @Update("UPDATE coach_course_session SET enrolled_count = enrolled_count + 1 "
        + "WHERE id = #{id} AND status = 'OPEN' AND enrolled_count < capacity")
    int occupySeatIfAvailable(@Param("id") Long id);

    // 释放占座（用于取消/退款等逆操作）：
    // - 这里的 UPDATE 没有要求状态必须为 OPEN，因为：
    //   - 课次可能已被教练 CLOSED/CANCELED，但历史订单取消仍需要释放名额
    // - CASE WHEN enrolled_count > 0 THEN enrolled_count - 1 ELSE 0：
    //   - 防止并发或重复释放导致 enrolled_count 变成负数
    //   - 对“重复调用释放”具备一定幂等性（多次调用也最多降到 0）
    // 注意：更严格的幂等通常还会结合 booking 的状态机（只对特定状态转移执行一次释放）。
    @Update("UPDATE coach_course_session SET enrolled_count = CASE WHEN enrolled_count > 0 THEN enrolled_count - 1 ELSE 0 END "
        + "WHERE id = #{id}")
    int releaseSeat(@Param("id") Long id);
}
