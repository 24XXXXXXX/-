package com.communitysport.wallet.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.wallet.entity.WalletTransaction;

/**
 * 钱包资金流水 Mapper。
 *
 * <p>除了基本的分页查询，本 Mapper 还承担一部分“统计聚合”查询：
 * <p>- 教练收入统计（COACH_COURSE_EARNING、COACH_VIDEO_EARNING）
 * <p>- 用户总入账/总出账
 * <p>- 指定时间范围内：支付/退款/充值/签到奖励的合计（用于管理员仪表盘等）
 *
 * <p>说明：
 * <p>- 这里使用 @Select 写 SQL，是为了更直观控制查询字段与性能
 * <p>- 对于金额统计，使用 COALESCE(SUM(...),0) 避免空结果返回 null
 */
@Mapper
public interface WalletTransactionMapper extends BaseMapper<WalletTransaction> {

    @Select("SELECT id, txn_no, user_id, txn_type, direction, amount, ref_type, ref_id, remark, created_at "
        + "FROM wallet_transaction WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    // 用户流水分页：按 id 倒序，越新的流水越靠前。
    List<WalletTransaction> selectPageByUserId(@Param("userId") Long userId, @Param("offset") long offset, @Param("limit") long limit);

    @Select("SELECT COUNT(1) FROM wallet_transaction WHERE user_id = #{userId}")
    // 用户流水总数：用于分页。
    long countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'IN' "
        + "AND txn_type IN ('COACH_COURSE_EARNING','COACH_VIDEO_EARNING')")
    // 教练收入明细条数：只统计指定 txn_type 的入账。
    long countCoachEarnings(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'IN'")
    // 用户总入账条数（所有 IN）。
    long countInByUserId(@Param("userId") Long userId);

    @Select("SELECT id, txn_no, user_id, txn_type, direction, amount, ref_type, ref_id, remark, created_at "
        + "FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'IN' "
        + "AND txn_type IN ('COACH_COURSE_EARNING','COACH_VIDEO_EARNING') "
        + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    // 教练收入分页明细。
    List<WalletTransaction> selectCoachEarningsPage(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("SELECT id, txn_no, user_id, txn_type, direction, amount, ref_type, ref_id, remark, created_at "
        + "FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'IN' "
        + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    // 用户所有入账分页明细。
    List<WalletTransaction> selectInPageByUserId(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'IN' "
        + "AND txn_type IN ('COACH_COURSE_EARNING','COACH_VIDEO_EARNING')")
    long sumCoachEarningsInAmount(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'IN'")
    long sumInAmountByUserId(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'OUT' "
        + "AND txn_type = 'WITHDRAW'")
    long sumWithdrawOutAmount(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(ABS(amount)), 0) FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'OUT'")
    long sumOutAmountByUserId(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE user_id = #{userId} "
        + "AND direction = 'OUT' "
        + "AND txn_type IN ('VENUE_BOOKING','COURSE_BOOKING','EQUIPMENT_ORDER','COACH_VIDEO')")
    long sumPayOutAmountByUserId(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE created_at >= #{start} AND created_at < #{end} "
        + "AND direction = 'OUT' "
        + "AND txn_type IN ('VENUE_BOOKING','COURSE_BOOKING','EQUIPMENT_ORDER','COACH_VIDEO')")
    long sumPayAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE created_at >= #{start} AND created_at < #{end} "
        + "AND direction = 'IN' "
        + "AND txn_type IN ('VENUE_REFUND','COURSE_REFUND')")
    long sumRefundAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE created_at >= #{start} AND created_at < #{end} "
        + "AND direction = 'IN' "
        + "AND txn_type = 'TOPUP'")
    long sumTopupAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction "
        + "WHERE created_at >= #{start} AND created_at < #{end} "
        + "AND direction = 'IN' "
        + "AND txn_type = 'SIGNIN'")
    long sumSigninRewardAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
