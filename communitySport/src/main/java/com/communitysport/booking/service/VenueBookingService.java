package com.communitysport.booking.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.booking.dto.BookingCreateRequest;
import com.communitysport.booking.dto.BookingDetailResponse;
import com.communitysport.booking.dto.BookingListItem;
import com.communitysport.booking.dto.BookingPageResponse;
import com.communitysport.booking.dto.BookingVerifyRequest;
import com.communitysport.booking.dto.BookingVerifyLogItem;
import com.communitysport.booking.entity.VenueBooking;
import com.communitysport.booking.entity.VenueVerificationLog;
import com.communitysport.booking.mapper.VenueBookingMapper;
import com.communitysport.booking.mapper.VenueVerificationLogMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.entity.VenueTimeslot;
import com.communitysport.venue.mapper.VenueMapper;
import com.communitysport.venue.mapper.VenueTimeslotMapper;
import com.communitysport.venuereview.entity.VenueReview;
import com.communitysport.venuereview.mapper.VenueReviewMapper;
import com.communitysport.wallet.service.WalletService;

/**
 * 场地预约订单服务（venue_booking）。
 *
 * <p>这一模块是“场地”业务真正的交易核心：
 * <p>- 它负责把「可预约时段 venue_timeslot」与「订单 venue_booking」绑定起来
 * <p>- 并把“支付/退款”统一落到钱包（WalletService）中，形成可审计的资金流水
 *
 * <p>你可以把它理解成一个小型的“状态机”：
 * <p>- 下单：AVAILABLE 时段 -> BOOKED（原子占用） -> 创建 booking -> 扣款 -> booking=PAID
 * <p>- 取消：booking=PAID 才退款；然后尝试把 timeslot 从 BOOKED 释放回 AVAILABLE
 * <p>- 核销：booking=PAID 且到达预约时间后 -> booking=USED，并记录核销日志
 *
 * <p>注意：本项目的“支付”是钱包余额支付，因此 create() 里会直接扣款。
 * 如果未来接入第三方支付（微信/支付宝），一般会把“扣款”改为：创建订单=CREATED -> 等待支付回调 -> 再置为 PAID。
 */
@Service
public class VenueBookingService {

    private final VenueBookingMapper venueBookingMapper;

    private final VenueVerificationLogMapper venueVerificationLogMapper;

    private final VenueTimeslotMapper venueTimeslotMapper;

    private final VenueMapper venueMapper;

    private final WalletService walletService;

    private final SysUserMapper sysUserMapper;

    private final VenueReviewMapper venueReviewMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    public VenueBookingService(
            VenueBookingMapper venueBookingMapper,
            VenueVerificationLogMapper venueVerificationLogMapper,
            VenueTimeslotMapper venueTimeslotMapper,
            VenueMapper venueMapper,
            WalletService walletService,
            SysUserMapper sysUserMapper,
            VenueReviewMapper venueReviewMapper
    ) {
        this.venueBookingMapper = venueBookingMapper;
        this.venueVerificationLogMapper = venueVerificationLogMapper;
        this.venueTimeslotMapper = venueTimeslotMapper;
        this.venueMapper = venueMapper;
        this.walletService = walletService;
        this.sysUserMapper = sysUserMapper;
        this.venueReviewMapper = venueReviewMapper;
    }

    @Transactional
    public BookingDetailResponse create(AuthenticatedUser principal, BookingCreateRequest request) {
        // 创建场地预约（下单 + 直接支付）。
        //
        // 关键一致性目标：
        // 1）同一时段只能被一个订单占用（并发下也成立）
        // 2）扣款成功则订单必须进入 PAID（资金与订单状态一致）
        // 3）任何异常要么整体回滚，要么以明确错误返回（HTTP 状态码）
        Long userId = requireUserId(principal);
        if (request == null || request.getTimeslotId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeslotId required");
        }

        VenueTimeslot timeslot = venueTimeslotMapper.selectById(request.getTimeslotId());
        if (timeslot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeslot not found");
        }
        if (!Objects.equals(timeslot.getStatus(), "AVAILABLE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timeslot not available");
        }
        // 如果时段已经开始了，就禁止下单：
        // - 防止用户“临近开始”钻空子下单
        // - 也简化核销逻辑（核销要求 now >= startTime）
        if (timeslot.getStartTime() != null && timeslot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timeslot already started");
        }

        Venue venue = venueMapper.selectById(timeslot.getVenueId());
        if (venue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        if (!Objects.equals(venue.getStatus(), "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue not active");
        }

        // 重要：原子占用时段。
        //
        // 这里不是简单 setStatus 再 updateById，而是：
        // UPDATE ... WHERE id=? AND status='AVAILABLE'
        //
        // 这样两个并发用户抢同一时段时：
        // - 只有一个请求能更新到 1 行
        // - 另一个请求 updated=0，返回 409 冲突
        int updated = venueTimeslotMapper.updateStatusIfMatch(timeslot.getId(), "BOOKED", "AVAILABLE");
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timeslot not available");
        }

        LocalDateTime now = LocalDateTime.now();

        VenueBooking booking = new VenueBooking();
        // bookingNo：对外展示/检索用编号（与数据库自增 id 区分开）。
        booking.setBookingNo(UUID.randomUUID().toString().replace("-", ""));
        booking.setUserId(userId);
        booking.setVenueId(timeslot.getVenueId());
        booking.setTimeslotId(timeslot.getId());
        booking.setAmount(timeslot.getPrice() == null ? 0 : timeslot.getPrice());
        // 初始状态先写 CREATED：表示“订单已创建，但还没完成支付/结算”。
        // 本项目会立即扣钱包，因此后面会立刻更新为 PAID。
        booking.setStatus("CREATED");
        // verificationCode：核销码（6 位）。用于现场工作人员核销验证。
        booking.setVerificationCode(genVerificationCode());
        booking.setCreatedAt(now);

        try {
            venueBookingMapper.insert(booking);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timeslot already booked");
        }

        int amount = booking.getAmount() == null ? 0 : booking.getAmount();
        if (amount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid amount");
        }

        if (amount > 0) {
            // 扣款：通过 WalletService 统一完成“余额减少 + 流水落库”。
            //
            // txnType/refType/refId：
            // - txnType 用于统计口径（例如仪表盘统计 VENUE_BOOKING 总额）
            // - refType/refId 用于把流水反查回业务订单（实现可追溯）
            //
            // debit 内部会用“余额原子扣减（balance>=amount）”避免透支。
            walletService.debit(userId, amount, "VENUE_BOOKING", "venue booking", "VENUE_BOOKING", booking.getId());
        }

        // 走到这里说明扣款成功（或金额为 0 无需扣款），把订单置为 PAID。
        // 仍在同一个事务中：确保“扣款成功 <-> 订单PAID”一致。
        booking.setStatus("PAID");
        booking.setPaidAt(now);
        venueBookingMapper.updateById(booking);

        return toDetail(booking);
    }

    @Transactional
    public BookingDetailResponse cancel(AuthenticatedUser principal, Long bookingId) {
        // 取消订单：
        // - 如果已支付（PAID）且金额>0：执行退款（钱包入账）并标记 REFUNDED
        // - 如果未支付/金额为0：直接标记 CANCELED
        //
        // 同时：尝试释放 timeslot（BOOKED -> AVAILABLE）。
        Long userId = requireUserId(principal);
        if (bookingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId required");
        }

        VenueBooking booking = venueBookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!Objects.equals(booking.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String status = booking.getStatus();
        if (Objects.equals(status, "USED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking already used");
        }
        // 幂等：重复取消同一笔订单时，直接返回当前详情。
        if (Objects.equals(status, "CANCELED") || Objects.equals(status, "REFUNDED")) {
            return toDetail(booking);
        }

        LocalDateTime now = LocalDateTime.now();
        int amount = booking.getAmount() == null ? 0 : booking.getAmount();

        if (Objects.equals(status, "PAID") && amount > 0) {
            // 退款：同样走 WalletService 统一入账 + 流水。
            // 注意：这里 refType 仍然指向订单，便于对账。
            walletService.credit(userId, amount, "VENUE_REFUND", "venue refund", "VENUE_BOOKING", booking.getId());
            booking.setStatus("REFUNDED");
        } else {
            booking.setStatus("CANCELED");
        }

        booking.setCanceledAt(now);
        venueBookingMapper.updateById(booking);

        if (booking.getTimeslotId() != null) {
            // 释放时段：
            // - 仍然用 updateStatusIfMatch 做条件更新
            // - 避免把其他状态（比如被后台 BLOCKED）误改回 AVAILABLE
            venueTimeslotMapper.updateStatusIfMatch(booking.getTimeslotId(), "AVAILABLE", "BOOKED");
        }

        return toDetail(booking);
    }

    @Transactional
    public BookingDetailResponse verify(Long staffUserId, BookingVerifyRequest request) {
        // 核销（工作人员现场确认用户到场）：
        //
        // 规则：
        // - 必须是 staff 身份（Controller 已 requireStaff，这里再次防御）
        // - 必须找到唯一的 booking（可以按 bookingNo，或者按核销码）
        // - 只能核销 PAID 的订单
        // - 核销时间必须 >= timeslot.startTime（未到时间不能核销）
        // - 核销成功后写入 venue_verification_log（用于审计）
        if (staffUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (request == null || !StringUtils.hasText(request.getVerificationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationCode required");
        }

        String bookingNo = request.getBookingNo();
        String verificationCode = request.getVerificationCode().trim();
        if (!StringUtils.hasText(verificationCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationCode required");
        }

        VenueBooking booking = null;
        if (StringUtils.hasText(bookingNo)) {
            // bookingNo 精确定位：不会出现歧义。
            booking = venueBookingMapper.selectOne(new LambdaQueryWrapper<VenueBooking>()
                .eq(VenueBooking::getBookingNo, bookingNo.trim()));
        } else {
            // 仅凭 verificationCode：理论上可能撞码（概率低，但仍然要处理）。
            // 这里额外要求 status=PAID，缩小范围。
            List<VenueBooking> rows = venueBookingMapper.selectList(new LambdaQueryWrapper<VenueBooking>()
                .eq(VenueBooking::getVerificationCode, verificationCode)
                .eq(VenueBooking::getStatus, "PAID"));
            if (rows == null || rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
            }
            if (rows.size() > 1) {
                // 多笔订单核销码相同：拒绝核销，要求用 bookingNo 精确核销。
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ambiguous verification code");
            }
            booking = rows.get(0);
        }
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (!Objects.equals(booking.getVerificationCode(), verificationCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "核销码错误");
        }
        if (!Objects.equals(booking.getStatus(), "PAID")) {
            // 文案里写 payable，实际含义是“可核销”。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking not payable");
        }

        LocalDateTime now = LocalDateTime.now();
        VenueTimeslot timeslot = booking.getTimeslotId() == null ? null : venueTimeslotMapper.selectById(booking.getTimeslotId());
        if (timeslot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeslot not found");
        }
        if (timeslot.getStartTime() != null && timeslot.getStartTime().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未到预约时间，无法核销");
        }

        // 状态进入 USED，代表服务已履约。
        // 这里会同时设置 usedAt，方便统计“使用次数”。
        booking.setStatus("USED");
        booking.setUsedAt(now);
        venueBookingMapper.updateById(booking);

        // 写核销日志：
        // - SUCCESS/FAIL 等结果
        // - staffUserId 方便追责/统计
        VenueVerificationLog log = new VenueVerificationLog();
        log.setBookingId(booking.getId());
        log.setStaffUserId(staffUserId);
        log.setVerifiedAt(now);
        log.setResult("SUCCESS");
        venueVerificationLogMapper.insert(log);

        return toDetail(booking);
    }

    public BookingPageResponse myBookings(AuthenticatedUser principal, Integer page, Integer size, String status) {
        Long userId = requireUserId(principal);
        return listBookings(page, size, userId, status, null);
    }

    public BookingDetailResponse myBookingDetail(AuthenticatedUser principal, Long id) {
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        VenueBooking booking = venueBookingMapper.selectById(id);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!Objects.equals(booking.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return toDetail(booking);
    }

    public List<BookingVerifyLogItem> myVerifyLogs(AuthenticatedUser principal, Long bookingId) {
        Long userId = requireUserId(principal);
        if (bookingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId required");
        }
        VenueBooking booking = venueBookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!Objects.equals(booking.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return listVerifyLogs(bookingId);
    }

    public BookingPageResponse adminBookings(Integer page, Integer size, Long userId, String status, String bookingNo) {
        return listBookings(page, size, userId, status, bookingNo);
    }

    public BookingDetailResponse adminBookingDetail(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        VenueBooking booking = venueBookingMapper.selectById(id);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return toDetail(booking);
    }

    public List<BookingVerifyLogItem> adminVerifyLogs(Long bookingId) {
        if (bookingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId required");
        }
        VenueBooking booking = venueBookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return listVerifyLogs(bookingId);
    }

    private BookingPageResponse listBookings(Integer page, Integer size, Long userId, String status, String bookingNo) {
        // 订单列表（用户端/管理员端共用）：分页 + 条件过滤。
        //
        // 这里把“筛选条件构造”独立到 buildBookingQuery 中，避免重复拼装。
        int p = page == null ? 1 : page.intValue();
        int s = size == null ? 20 : size.intValue();
        if (p < 1) {
            p = 1;
        }
        if (s < 1) {
            s = 1;
        }
        if (s > 100) {
            s = 100;
        }

        LambdaQueryWrapper<VenueBooking> countQw = buildBookingQuery(userId, status, bookingNo);
        long total = venueBookingMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<VenueBooking> rows = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<VenueBooking> listQw = buildBookingQuery(userId, status, bookingNo)
                .orderByDesc(VenueBooking::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            rows = venueBookingMapper.selectList(listQw);
        }

        Set<Long> bookingIds = rows.stream().map(VenueBooking::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        // reviewed/verified 两个集合都是“为了列表展示”的派生字段：
        // - reviewed：该订单是否已经评价（venue_review 表）
        // - verified：该订单是否存在核销日志（venue_verification_log 表）
        Set<Long> reviewedBookingIds = new HashSet<>();
        if (!bookingIds.isEmpty()) {
            List<VenueReview> reviews = venueReviewMapper.selectList(new LambdaQueryWrapper<VenueReview>()
                .in(VenueReview::getBookingId, bookingIds)
            );
            if (reviews != null) {
                for (VenueReview r : reviews) {
                    if (r != null && r.getBookingId() != null) {
                        reviewedBookingIds.add(r.getBookingId());
                    }
                }
            }
        }

        Set<Long> verifiedBookingIds = new HashSet<>();
        if (!bookingIds.isEmpty()) {
            List<VenueVerificationLog> verifyLogs = venueVerificationLogMapper.selectList(
                new LambdaQueryWrapper<VenueVerificationLog>()
                    .in(VenueVerificationLog::getBookingId, bookingIds)
            );
            if (verifyLogs != null) {
                for (VenueVerificationLog l : verifyLogs) {
                    if (l != null && l.getBookingId() != null) {
                        verifiedBookingIds.add(l.getBookingId());
                    }
                }
            }
        }

        List<BookingListItem> items = toListItems(rows, reviewedBookingIds, verifiedBookingIds);

        BookingPageResponse resp = new BookingPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private LambdaQueryWrapper<VenueBooking> buildBookingQuery(Long userId, String status, String bookingNo) {
        // 构造订单筛选条件。
        //
        // 这里有一个“看起来绕”的点：PAID/USED 的筛选不仅看 booking.status，
        // 还会综合 usedAt 以及 verification_log 是否存在。
        //
        // 原因：历史数据/兼容逻辑中，可能出现：
        // - status 仍为 PAID，但已写核销日志（或 usedAt 不为空）
        // 因此“对外展示状态”会用 effectiveStatus 进行二次推导。
        LambdaQueryWrapper<VenueBooking> qw = new LambdaQueryWrapper<>();
        if (userId != null) {
            qw.eq(VenueBooking::getUserId, userId);
        }
        if (StringUtils.hasText(status)) {
            String s = status.trim();
            if (s.contains(",")) {
                List<String> statuses = Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
                if (statuses.size() == 1) {
                    String st = statuses.get(0);
                    if (Objects.equals(st, "PAID")) {
                        // 筛选“未使用的已支付订单”：
                        // - status=PAID
                        // - usedAt is null
                        // - 并且没有核销日志
                        qw.eq(VenueBooking::getStatus, "PAID")
                            .isNull(VenueBooking::getUsedAt)
                            .apply("NOT EXISTS (SELECT 1 FROM venue_verification_log l WHERE l.booking_id = venue_booking.id)");
                    } else if (Objects.equals(st, "USED")) {
                        // 筛选“已使用订单”：
                        // - status=USED 或 usedAt 不为空 或 存在核销日志
                        qw.and(w -> w.eq(VenueBooking::getStatus, "USED")
                            .or().isNotNull(VenueBooking::getUsedAt)
                            .or().apply("EXISTS (SELECT 1 FROM venue_verification_log l WHERE l.booking_id = venue_booking.id)"));
                    } else {
                        qw.eq(VenueBooking::getStatus, st);
                    }
                } else if (!statuses.isEmpty()) {
                    qw.in(VenueBooking::getStatus, statuses);
                }
            } else {
                if (Objects.equals(s, "PAID")) {
                    // 单个状态筛选：逻辑同上。
                    qw.eq(VenueBooking::getStatus, "PAID")
                        .isNull(VenueBooking::getUsedAt)
                        .apply("NOT EXISTS (SELECT 1 FROM venue_verification_log l WHERE l.booking_id = venue_booking.id)");
                } else if (Objects.equals(s, "USED")) {
                    qw.and(w -> w.eq(VenueBooking::getStatus, "USED")
                        .or().isNotNull(VenueBooking::getUsedAt)
                        .or().apply("EXISTS (SELECT 1 FROM venue_verification_log l WHERE l.booking_id = venue_booking.id)"));
                } else {
                    qw.eq(VenueBooking::getStatus, s);
                }
            }
        }
        if (StringUtils.hasText(bookingNo)) {
            qw.eq(VenueBooking::getBookingNo, bookingNo);
        }
        return qw;
    }

    private List<BookingListItem> toListItems(List<VenueBooking> rows, Set<Long> reviewedBookingIds, Set<Long> verifiedBookingIds) {
        // 把 DB 行转换成“列表展示 DTO”。
        //
        // 性能考虑：这里做了批量加载（selectByIds），避免 N+1 查询。
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> venueIds = rows.stream().map(VenueBooking::getVenueId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = rows.stream().map(VenueBooking::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> timeslotIds = rows.stream().map(VenueBooking::getTimeslotId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Venue> venueMap = new HashMap<>();
        if (!venueIds.isEmpty()) {
            List<Venue> venues = venueMapper.selectByIds(venueIds);
            for (Venue v : venues) {
                venueMap.put(v.getId(), v);
            }
        }

        Map<Long, SysUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectByIds(userIds);
            for (SysUser u : users) {
                userMap.put(u.getId(), u);
            }
        }

        Map<Long, VenueTimeslot> timeslotMap = new HashMap<>();
        if (!timeslotIds.isEmpty()) {
            List<VenueTimeslot> slots = venueTimeslotMapper.selectByIds(timeslotIds);
            for (VenueTimeslot t : slots) {
                timeslotMap.put(t.getId(), t);
            }
        }

        List<BookingListItem> items = new ArrayList<>();
        for (VenueBooking b : rows) {
            BookingListItem item = new BookingListItem();
            item.setId(b.getId());
            item.setBookingNo(b.getBookingNo());
            item.setUserId(b.getUserId());
            SysUser u = b.getUserId() == null ? null : userMap.get(b.getUserId());
            item.setUsername(u == null ? null : u.getUsername());
            item.setVenueId(b.getVenueId());
            Venue v = b.getVenueId() == null ? null : venueMap.get(b.getVenueId());
            item.setVenueName(v == null ? null : v.getName());
            item.setVenueArea(v == null ? null : v.getArea());
            item.setVenueAddress(v == null ? null : v.getAddress());
            item.setTimeslotId(b.getTimeslotId());
            VenueTimeslot ts = b.getTimeslotId() == null ? null : timeslotMap.get(b.getTimeslotId());
            item.setStartTime(ts == null ? null : ts.getStartTime());
            item.setEndTime(ts == null ? null : ts.getEndTime());
            item.setAmount(b.getAmount());

            String effectiveStatus = b.getStatus();
            if (Objects.equals(effectiveStatus, "PAID")) {
                // effectiveStatus：对外展示状态。
                //
                // 因为历史/兼容原因，USED 的判断可能不完全依赖 status 字段：
                // - usedAt 不为空
                // - 或核销日志存在
                boolean used = b.getUsedAt() != null;
                if (!used && b.getId() != null && verifiedBookingIds != null) {
                    used = verifiedBookingIds.contains(b.getId());
                }
                if (used) {
                    effectiveStatus = "USED";
                }
            }
            item.setStatus(effectiveStatus);
            item.setVerificationCode(b.getVerificationCode());
            item.setReviewed(b.getId() != null && reviewedBookingIds != null && reviewedBookingIds.contains(b.getId()));
            item.setPaidAt(b.getPaidAt());
            item.setCanceledAt(b.getCanceledAt());
            item.setUsedAt(b.getUsedAt());
            item.setCreatedAt(b.getCreatedAt());
            items.add(item);
        }
        return items;
    }

    private List<BookingVerifyLogItem> listVerifyLogs(Long bookingId) {
        List<VenueVerificationLog> rows = venueVerificationLogMapper.selectList(
            new LambdaQueryWrapper<VenueVerificationLog>()
                .eq(VenueVerificationLog::getBookingId, bookingId)
                .orderByDesc(VenueVerificationLog::getId)
        );

        Set<Long> staffIds = new HashSet<>();
        if (rows != null) {
            for (VenueVerificationLog r : rows) {
                if (r != null && r.getStaffUserId() != null) {
                    staffIds.add(r.getStaffUserId());
                }
            }
        }

        Map<Long, SysUser> staffMap = new HashMap<>();
        if (!staffIds.isEmpty()) {
            List<SysUser> staff = sysUserMapper.selectByIds(staffIds);
            for (SysUser u : staff) {
                staffMap.put(u.getId(), u);
            }
        }

        List<BookingVerifyLogItem> items = new ArrayList<>();
        if (rows != null) {
            for (VenueVerificationLog r : rows) {
                BookingVerifyLogItem item = new BookingVerifyLogItem();
                item.setId(r.getId());
                item.setBookingId(r.getBookingId());
                item.setStaffUserId(r.getStaffUserId());
                SysUser u = r.getStaffUserId() == null ? null : staffMap.get(r.getStaffUserId());
                item.setStaffUsername(u == null ? null : u.getUsername());
                item.setVerifiedAt(r.getVerifiedAt());
                item.setResult(r.getResult());
                item.setRemark(r.getRemark());
                items.add(item);
            }
        }
        return items;
    }

    private BookingDetailResponse toDetail(VenueBooking booking) {
        // 订单详情：相较于列表，会多查一些字段（例如用户/场地名）。
        BookingDetailResponse resp = new BookingDetailResponse();
        resp.setId(booking.getId());
        resp.setBookingNo(booking.getBookingNo());
        resp.setUserId(booking.getUserId());
        if (booking.getUserId() != null) {
            SysUser u = sysUserMapper.selectById(booking.getUserId());
            resp.setUsername(u == null ? null : u.getUsername());
        }
        resp.setVenueId(booking.getVenueId());
        if (booking.getVenueId() != null) {
            Venue v = venueMapper.selectById(booking.getVenueId());
            resp.setVenueName(v == null ? null : v.getName());
        }
        resp.setTimeslotId(booking.getTimeslotId());
        if (booking.getTimeslotId() != null) {
            VenueTimeslot ts = venueTimeslotMapper.selectById(booking.getTimeslotId());
            resp.setStartTime(ts == null ? null : ts.getStartTime());
            resp.setEndTime(ts == null ? null : ts.getEndTime());
        }
        resp.setAmount(booking.getAmount());

        String effectiveStatus = booking.getStatus();
        if (Objects.equals(effectiveStatus, "PAID")) {
            // 详情页同样对 PAID 做“是否已使用”的推导。
            // 这里用 count 查询核销日志，保证即使列表未传 verifiedBookingIds 也能正确展示。
            boolean used = booking.getUsedAt() != null;
            if (!used && booking.getId() != null) {
                long verifyCnt = venueVerificationLogMapper.selectCount(
                    new LambdaQueryWrapper<VenueVerificationLog>().eq(VenueVerificationLog::getBookingId, booking.getId())
                );
                used = verifyCnt > 0;
            }
            if (used) {
                effectiveStatus = "USED";
            }
        }
        resp.setStatus(effectiveStatus);
        resp.setVerificationCode(booking.getVerificationCode());
        boolean reviewed = false;
        if (booking.getId() != null) {
            // reviewed：是否已评价。
            long cnt = venueReviewMapper.selectCount(new LambdaQueryWrapper<VenueReview>().eq(VenueReview::getBookingId, booking.getId()));
            reviewed = cnt > 0;
        }
        resp.setReviewed(reviewed);
        resp.setPaidAt(booking.getPaidAt());
        resp.setCanceledAt(booking.getCanceledAt());
        resp.setUsedAt(booking.getUsedAt());
        resp.setCreatedAt(booking.getCreatedAt());
        return resp;
    }

    private String genVerificationCode() {
        int n = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(n);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
