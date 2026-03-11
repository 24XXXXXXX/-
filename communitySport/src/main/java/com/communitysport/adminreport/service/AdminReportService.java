package com.communitysport.adminreport.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.adminreport.dto.AdminMetricsResponse;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.booking.mapper.VenueBookingMapper;
import com.communitysport.complaint.mapper.ComplaintMapper;
import com.communitysport.coursebooking.mapper.CoachCourseBookingMapper;
import com.communitysport.equipment.mapper.EquipmentOrderMapper;
import com.communitysport.venue.mapper.VenueTimeslotMapper;
import com.communitysport.video.mapper.CoachVideoPurchaseMapper;
import com.communitysport.wallet.mapper.WalletTransactionMapper;

/**
 * 管理员报表/仪表盘服务。
 *
 * <p>这个模块的定位：给管理员端 Dashboard 提供“区间指标汇总”。
 * <p>它不会返回明细列表，而是返回一组统计数字，例如：
 * <p>- 新增用户数、活跃用户数
 * <p>- 支付金额、退款金额、充值金额、签到奖励发放金额
 * <p>- 场地利用率、各类订单/投诉数量等
 *
 * <p>实现特点：
 * <p>- 大部分统计直接由数据库聚合完成（COUNT/SUM），避免把大量数据拉到内存计算
 * <p>- 钱包相关的金额统计统一来自 wallet_transaction（因为它是“资金审计日志”）
 */
@Service
public class AdminReportService {

    // 管理端-仪表盘指标汇总服务：
    // - 关注“区间内发生了什么”（新增/活跃/支付/退款/核销/投诉等）
    // - 不返回明细，尽量用数据库聚合（COUNT/SUM）直接算出结果
    //
    // 统计口径约定：
    // - 日期入参（startDate/endDate，均包含）会被转换成半开区间 [start, endExclusive)
    // - 金额字段通常以“最小单位整数”表示（例如分/积分），避免浮点误差

    private final SysUserMapper sysUserMapper;

    private final WalletTransactionMapper walletTransactionMapper;

    private final VenueTimeslotMapper venueTimeslotMapper;

    private final VenueBookingMapper venueBookingMapper;

    private final CoachCourseBookingMapper coachCourseBookingMapper;

    private final EquipmentOrderMapper equipmentOrderMapper;

    private final CoachVideoPurchaseMapper coachVideoPurchaseMapper;

    private final ComplaintMapper complaintMapper;

    public AdminReportService(
            SysUserMapper sysUserMapper,
            WalletTransactionMapper walletTransactionMapper,
            VenueTimeslotMapper venueTimeslotMapper,
            VenueBookingMapper venueBookingMapper,
            CoachCourseBookingMapper coachCourseBookingMapper,
            EquipmentOrderMapper equipmentOrderMapper,
            CoachVideoPurchaseMapper coachVideoPurchaseMapper,
            ComplaintMapper complaintMapper
    ) {
        this.sysUserMapper = sysUserMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.venueTimeslotMapper = venueTimeslotMapper;
        this.venueBookingMapper = venueBookingMapper;
        this.coachCourseBookingMapper = coachCourseBookingMapper;
        this.equipmentOrderMapper = equipmentOrderMapper;
        this.coachVideoPurchaseMapper = coachVideoPurchaseMapper;
        this.complaintMapper = complaintMapper;
    }

    public AdminMetricsResponse metrics(LocalDate startDate, LocalDate endDate) {
        // 指标查询：按照“日期区间”统计。
        //
        // 入参语义：
        // - startDate：起始日期（包含）
        // - endDate：结束日期（包含）
        //
        // 实现方式：转成 [start, endExclusive) 的时间区间。
        // 这样做的好处：
        // - 不用处理 23:59:59.999 的边界
        // - SQL 写起来统一：created_at >= start AND created_at < endExclusive
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate/endDate required");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be >= startDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        // 半开区间 [start, endExclusive) 的好处：
        // - 避免 endDate 当天 23:59:59.999 的边界处理
        // - 避免跨天边界重复计数

        long newUsers = sysUserMapper.countCreatedBetween(start, endExclusive);
        long activeUsers = sysUserMapper.countLastLoginBetween(start, endExclusive);

        // 钱包指标：
        // 这些金额来自 wallet_transaction 的聚合。
        //
        // 注意口径：
        // - totalPayAmount：用户发生的“出账支付总额”（direction=OUT 且 txn_type 属于支付类型）
        // - totalRefundAmount：发生的“退款入账总额”（direction=IN 且 txn_type 属于退款类型）
        // - totalTopupAmount：管理员审批通过后发放的“充值入账总额”（txn_type=TOPUP）
        // - signinRewardAmount：签到奖励入账总额（txn_type=SIGNIN）
        //
        // 口径为什么以 wallet_transaction 为准？
        // - 因为 balance 是状态，会变化；流水是历史，适合做统计与审计
        long totalPayAmount = walletTransactionMapper.sumPayAmountBetween(start, endExclusive);
        long totalRefundAmount = walletTransactionMapper.sumRefundAmountBetween(start, endExclusive);
        long totalTopupAmount = walletTransactionMapper.sumTopupAmountBetween(start, endExclusive);
        long signinRewardAmount = walletTransactionMapper.sumSigninRewardAmountBetween(start, endExclusive);

        // 说明：金额统计选用 wallet_transaction 的聚合，是为了审计一致性：
        // - balance 是状态，可能随时间变化；流水是历史记录，更适合作为统计/审计依据

        long venueTimeslotTotal = venueTimeslotMapper.countByStartTimeBetween(start, endExclusive);
        long venueTimeslotBooked = venueTimeslotMapper.countBookedByStartTimeBetween(start, endExclusive);

        double venueUtilizationRate = 0d;
        if (venueTimeslotTotal > 0) {
            // 利用率 = 已被预订的时段数 / 总可用时段数
            // - 这里用 double 仅用于比例展示，不参与资金计算
            venueUtilizationRate = (double) venueTimeslotBooked / (double) venueTimeslotTotal;
        }

        long venueBookingsCreated = venueBookingMapper.countCreatedBetween(start, endExclusive);
        long venueBookingsUsed = venueBookingMapper.countUsedBetween(start, endExclusive);

        long courseBookingsCreated = coachCourseBookingMapper.countCreatedBetween(start, endExclusive);
        long courseBookingsUsed = coachCourseBookingMapper.countUsedBetween(start, endExclusive);

        long equipmentOrdersCreated = equipmentOrderMapper.countCreatedBetween(start, endExclusive);
        long equipmentOrdersPaidAmount = equipmentOrderMapper.sumPaidAmountBetween(start, endExclusive);

        long videoPurchasesCreated = coachVideoPurchaseMapper.countPaidCreatedBetween(start, endExclusive);

        long complaintsCreated = complaintMapper.countCreatedBetween(start, endExclusive);
        long complaintsResolved = complaintMapper.countResolvedBetween(start, endExclusive);

        double complaintResolutionRate = 0d;
        if (complaintsCreated > 0) {
            // 投诉解决率 = 区间内解决数 / 区间内新建数
            complaintResolutionRate = (double) complaintsResolved / (double) complaintsCreated;
        }

        AdminMetricsResponse resp = new AdminMetricsResponse();
        resp.setStartDate(startDate);
        resp.setEndDate(endDate);
        resp.setNewUsers(newUsers);
        resp.setActiveUsers(activeUsers);
        resp.setTotalPayAmount(totalPayAmount);
        resp.setTotalRefundAmount(totalRefundAmount);
        resp.setTotalTopupAmount(totalTopupAmount);
        resp.setSigninRewardAmount(signinRewardAmount);
        resp.setVenueTimeslotTotal(venueTimeslotTotal);
        resp.setVenueTimeslotBooked(venueTimeslotBooked);
        resp.setVenueUtilizationRate(venueUtilizationRate);
        resp.setVenueBookingsCreated(venueBookingsCreated);
        resp.setVenueBookingsUsed(venueBookingsUsed);
        resp.setCourseBookingsCreated(courseBookingsCreated);
        resp.setCourseBookingsUsed(courseBookingsUsed);
        resp.setEquipmentOrdersCreated(equipmentOrdersCreated);
        resp.setEquipmentOrdersPaidAmount(equipmentOrdersPaidAmount);
        resp.setVideoPurchasesCreated(videoPurchasesCreated);
        resp.setComplaintsCreated(complaintsCreated);
        resp.setComplaintsResolved(complaintsResolved);
        resp.setComplaintResolutionRate(complaintResolutionRate);
        return resp;
    }

    public String getConfigValueOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
