package com.communitysport.coursebooking.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.course.entity.CoachCourse;
import com.communitysport.course.entity.CoachCourseSession;
import com.communitysport.course.mapper.CoachCourseMapper;
import com.communitysport.course.mapper.CoachCourseSessionMapper;
import com.communitysport.coursebooking.dto.CourseBookingCreateRequest;
import com.communitysport.coursebooking.dto.CourseBookingDecisionRequest;
import com.communitysport.coursebooking.dto.CourseBookingDetailResponse;
import com.communitysport.coursebooking.dto.CourseBookingListItem;
import com.communitysport.coursebooking.dto.CourseBookingPageResponse;
import com.communitysport.coursebooking.dto.CourseBookingVerifyRequest;
import com.communitysport.coursebooking.entity.CoachCourseBooking;
import com.communitysport.coursebooking.mapper.CoachCourseBookingMapper;
import com.communitysport.coursereview.entity.CourseReview;
import com.communitysport.coursereview.mapper.CourseReviewMapper;
import com.communitysport.message.service.UserMessageService;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.mapper.VenueMapper;
import com.communitysport.wallet.service.WalletService;

@Service
public class CourseBookingService {

    // 课程预约服务（Course Booking）。
    //
    // 与“场地预约”类似，这里同样是一个典型的“订单 + 资源占用 + 支付/退款 + 核销交付”的组合业务：
    // - 资源：课次（coach_course_session）的名额 enrolled_count/capacity
    // - 订单：coach_course_booking
    // - 支付：WalletService.debit（扣用户余额）
    // - 退款：WalletService.credit（退回用户余额）
    // - 交付：核销（PAID -> USED）并给教练入账（COACH_COURSE_EARNING）
    //
    // 这里需要重点理解三个工程化主题：
    // 1) 并发：多人同时抢同一课次的最后一个名额，必须用“条件更新”保证不会超卖
    // 2) 幂等：取消/拒单等操作可能被重复点击，不能导致名额被重复释放或资金重复退回
    // 3) 一致性：订单状态、名额、钱包流水必须在事务中保持一致（要么都成功，要么都失败）

    private final CoachCourseBookingMapper coachCourseBookingMapper;

    private final CoachCourseSessionMapper coachCourseSessionMapper;

    private final CoachCourseMapper coachCourseMapper;

    private final SysUserMapper sysUserMapper;

    private final WalletService walletService;

    private final UserMessageService userMessageService;

    private final CourseReviewMapper courseReviewMapper;

    private final VenueMapper venueMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    public CourseBookingService(
            CoachCourseBookingMapper coachCourseBookingMapper,
            CoachCourseSessionMapper coachCourseSessionMapper,
            CoachCourseMapper coachCourseMapper,
            SysUserMapper sysUserMapper,
            WalletService walletService,
            UserMessageService userMessageService,
            CourseReviewMapper courseReviewMapper,
            VenueMapper venueMapper
    ) {
        this.coachCourseBookingMapper = coachCourseBookingMapper;
        this.coachCourseSessionMapper = coachCourseSessionMapper;
        this.coachCourseMapper = coachCourseMapper;
        this.sysUserMapper = sysUserMapper;
        this.walletService = walletService;
        this.userMessageService = userMessageService;
        this.courseReviewMapper = courseReviewMapper;
        this.venueMapper = venueMapper;
    }

    @Transactional
    public CourseBookingDetailResponse create(AuthenticatedUser principal, CourseBookingCreateRequest request) {
        // 用户端：创建课程预约单。
        //
        // 业务节奏设计：先“占座 + 下单”，再由教练决定是否接单，接单后用户才支付。
        // 这样做的好处：
        // - 教练可以根据自身时间安排筛选订单
        // - 用户避免“已付款但教练拒绝”的体验（当然也能走自动退款，但更复杂）
        //
        // 状态机（简化）：
        // PENDING_COACH --(教练接单)--> ACCEPTED --(用户支付)--> PAID --(核销)--> USED
        // PENDING_COACH --(教练拒单)--> REJECTED
        // ACCEPTED/PAID --(用户取消)--> CANCELED/REFUNDED
        Long userId = requireUserId(principal);
        if (request == null || request.getSessionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required");
        }

        long dup = coachCourseBookingMapper.selectCount(new LambdaQueryWrapper<CoachCourseBooking>()
            .eq(CoachCourseBooking::getUserId, userId)
            .eq(CoachCourseBooking::getCourseSessionId, request.getSessionId())
            .in(CoachCourseBooking::getStatus, List.of("PENDING_COACH", "ACCEPTED", "PAID"))
        );
        // 幂等/去重：
        // - 同一个用户对同一个 session，在“未结束”的状态（待教练/已接单/已支付）下只能存在一单
        // - 避免重复点击“预约”导致占用多个名额
        if (dup > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already booked");
        }

        CoachCourseSession session = coachCourseSessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        if (!Objects.equals(session.getStatus(), "OPEN")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session not open");
        }
        if (session.getStartTime() != null && session.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session already started");
        }

        CoachCourse course = session.getCourseId() == null ? null : coachCourseMapper.selectById(session.getCourseId());
        if (course == null || !Objects.equals(course.getStatus(), "ON_SALE")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        int updated = coachCourseSessionMapper.occupySeatIfAvailable(session.getId());
        // 并发关键点：占座采用“条件更新”方式（CAS 思想）。
        //
        // occupySeatIfAvailable 的 SQL：
        // UPDATE ... SET enrolled_count = enrolled_count + 1
        // WHERE id=? AND status='OPEN' AND enrolled_count < capacity
        //
        // 语义：
        // - 只有当课次仍 OPEN 且未满员时才会 +1
        // - 多人并发抢占时，数据库会保证只有有限的线程能更新成功
        // - 返回值 updated=1 表示抢占成功；updated=0 表示已经满员或被关闭
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session full or closed");
        }

        LocalDateTime now = LocalDateTime.now();

        CoachCourseBooking booking = new CoachCourseBooking();
        // bookingNo：业务唯一订单号（便于对外展示/搜索/核销定位）。
        // verificationCode：6位核销码（更适合线下口述/扫码输入）。
        // 注意：verificationCode 随机生成，理论上可能“碰撞重复”，因此核销时需要做“歧义处理”。
        booking.setBookingNo(UUID.randomUUID().toString().replace("-", ""));
        booking.setUserId(userId);
        booking.setCourseSessionId(session.getId());
        booking.setAmount(course.getPrice() == null ? 0 : course.getPrice());
        booking.setStatus("PENDING_COACH");
        booking.setVerificationCode(genVerificationCode());
        booking.setCreatedAt(now);

        coachCourseBookingMapper.insert(booking);

        userMessageService.createMessage(userId, "COURSE_BOOKING", "课程预约已提交", "你的课程预约已提交，等待教练确认。", "COACH_COURSE_BOOKING", booking.getId());
        userMessageService.createMessage(course.getCoachUserId(), "COURSE_BOOKING", "有新的课程预约", "你有新的课程预约待处理。", "COACH_COURSE_BOOKING", booking.getId());

        CoachCourseBooking after = booking.getId() == null ? null : coachCourseBookingMapper.selectById(booking.getId());
        return toDetail(after);
    }

    @Transactional
    public CourseBookingDetailResponse pay(AuthenticatedUser principal, Long id) {
        // 用户端：支付预约单。
        //
        // 一致性要求：
        // - 扣钱包（debit）与订单置为 PAID 必须在同一事务里
        // - 若扣款失败（余额不足/并发等），订单状态必须保持不变
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachCourseBooking booking = coachCourseBookingMapper.selectById(id);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!Objects.equals(booking.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!Objects.equals(booking.getStatus(), "ACCEPTED")) {
            // 只允许从 ACCEPTED 进入 PAID。
            // - 防止跳过教练确认
            // - 防止重复支付（PAID/USED 等状态再次支付）
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking not payable");
        }

        int amount = booking.getAmount() == null ? 0 : booking.getAmount();
        if (amount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid amount");
        }

        if (amount > 0) {
            // 钱包扣款：内部会执行“余额原子扣减 + 流水入账”。
            // refType/refId 用于把流水与业务订单关联起来，便于审计与对账。
            walletService.debit(userId, amount, "COURSE_BOOKING", "course booking", "COACH_COURSE_BOOKING", booking.getId());
        }

        booking.setStatus("PAID");
        booking.setPaidAt(LocalDateTime.now());
        coachCourseBookingMapper.updateById(booking);

        userMessageService.createMessage(userId, "COURSE_BOOKING", "课程预约已支付", "你的课程预约已支付成功。", "COACH_COURSE_BOOKING", booking.getId());
        return toDetail(booking);
    }

    @Transactional
    public CourseBookingDetailResponse cancel(AuthenticatedUser principal, Long id) {
        // 用户端：取消预约。
        //
        // 幂等设计：
        // - 若已经取消/已退款，重复调用直接返回详情
        //
        // 资源释放：
        // - 取消/拒单会释放名额 enrolled_count（releaseSeat）
        // - releaseSeat 自身是“幂等式减1”（不会减成负数）
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachCourseBooking booking = coachCourseBookingMapper.selectById(id);
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
        if (Objects.equals(status, "CANCELED") || Objects.equals(status, "REFUNDED")) {
            return toDetail(booking);
        }

        LocalDateTime now = LocalDateTime.now();
        int amount = booking.getAmount() == null ? 0 : booking.getAmount();

        if (Objects.equals(status, "PAID") && amount > 0) {
            // 已支付的取消：走退款（credit），并把订单改为 REFUNDED。
            // 这里与场地预约一样遵循“资金与状态在同一事务里”原则。
            walletService.credit(userId, amount, "COURSE_REFUND", "course refund", "COACH_COURSE_BOOKING", booking.getId());
            booking.setStatus("REFUNDED");
        } else {
            booking.setStatus("CANCELED");
        }

        coachCourseBookingMapper.updateById(booking);

        if (booking.getCourseSessionId() != null) {
            // 释放名额：取消后让出 enrolled_count。
            coachCourseSessionMapper.releaseSeat(booking.getCourseSessionId());
        }

        return toDetail(booking);
    }

    @Transactional
    public CourseBookingDetailResponse coachAccept(AuthenticatedUser principal, Long id) {
        // 教练端：接单。
        //
        // 注意：这里只改订单状态，不涉及资金；支付发生在用户端 pay。
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachCourseBooking booking = coachCourseBookingMapper.selectById(id);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        CoachCourse course = requireCoachCourseByBookingAndCoach(booking, coachUserId);

        if (!Objects.equals(booking.getStatus(), "PENDING_COACH")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking already processed");
        }

        booking.setStatus("ACCEPTED");
        booking.setCoachDecisionAt(LocalDateTime.now());
        coachCourseBookingMapper.updateById(booking);

        userMessageService.createMessage(booking.getUserId(), "COURSE_BOOKING", "教练已接单", "你的课程预约已被教练接单，请及时完成支付。", "COACH_COURSE_BOOKING", booking.getId());
        return toDetail(booking);
    }

    @Transactional
    public CourseBookingDetailResponse coachReject(AuthenticatedUser principal, Long id, CourseBookingDecisionRequest request) {
        // 教练端：拒单。
        //
        // 业务后果：
        // - 订单进入 REJECTED
        // - 释放名额（否则名额会被永久占用，造成“伪满员”）
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachCourseBooking booking = coachCourseBookingMapper.selectById(id);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        CoachCourse course = requireCoachCourseByBookingAndCoach(booking, coachUserId);

        if (!Objects.equals(booking.getStatus(), "PENDING_COACH")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking already processed");
        }

        booking.setStatus("REJECTED");
        booking.setCoachDecisionAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRejectReason())) {
            booking.setRejectReason(request.getRejectReason().trim());
        }

        coachCourseBookingMapper.updateById(booking);

        userMessageService.createMessage(booking.getUserId(), "COURSE_BOOKING", "教练已拒单", "你的课程预约被教练拒绝。", "COACH_COURSE_BOOKING", booking.getId());

        if (booking.getCourseSessionId() != null) {
            coachCourseSessionMapper.releaseSeat(booking.getCourseSessionId());
        }

        return toDetail(booking);
    }

    @Transactional
    public CourseBookingDetailResponse coachVerify(AuthenticatedUser principal, CourseBookingVerifyRequest request) {
        // 教练端：核销（交付）。
        //
        // 核销的两个核心动作：
        // 1) 订单 PAID -> USED（表示服务完成）
        // 2) 给教练入账（WalletService.credit，txnType=COACH_COURSE_EARNING）
        //
        // 也因此，核销逻辑需要非常谨慎：
        // - 只能核销 PAID 的订单（否则可能重复入账/未付款就入账）
        // - 需要校验上课时间（未到时间不能核销）
        Long coachUserId = requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getVerificationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationCode required");
        }

        String bookingNo = request.getBookingNo();
        String verificationCode = request.getVerificationCode().trim();
        if (!StringUtils.hasText(verificationCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationCode required");
        }

        CoachCourseBooking booking = null;
        if (StringUtils.hasText(bookingNo)) {
            // 优先用 bookingNo 定位：业务唯一，最稳定。
            booking = coachCourseBookingMapper.selectOne(
                new LambdaQueryWrapper<CoachCourseBooking>().eq(CoachCourseBooking::getBookingNo, bookingNo.trim())
            );
        } else {
            // 若只提供 verificationCode：需要考虑“随机码碰撞”的可能性。
            // 因此这里会先查出所有同码订单，再用“是否属于当前教练”的规则进行过滤，避免越权核销。
            List<CoachCourseBooking> rows = coachCourseBookingMapper.selectList(
                new LambdaQueryWrapper<CoachCourseBooking>().eq(CoachCourseBooking::getVerificationCode, verificationCode)
            );
            if (rows != null) {
                List<CoachCourseBooking> matched = new ArrayList<>();
                for (CoachCourseBooking r : rows) {
                    if (r == null) {
                        continue;
                    }
                    try {
                        requireCoachCourseByBookingAndCoach(r, coachUserId);
                        matched.add(r);
                    } catch (Exception ex) {
                    }
                }
                if (matched.size() == 1) {
                    booking = matched.get(0);
                } else if (matched.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
                } else {
                    // 多条匹配：说明核销码发生了“歧义”。
                    // 此时必须让前端改用 bookingNo 或更精确的方式，否则会核销错单。
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "ambiguous verification code");
                }
            }
        }
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        CoachCourse course = requireCoachCourseByBookingAndCoach(booking, coachUserId);

        if (!Objects.equals(booking.getVerificationCode(), verificationCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "核销码错误");
        }
        if (!Objects.equals(booking.getStatus(), "PAID")) {
            // 文案虽然写的是 payable，但语义是：只有 PAID 才能核销。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking not payable");
        }

        CoachCourseSession session = booking.getCourseSessionId() == null ? null : coachCourseSessionMapper.selectById(booking.getCourseSessionId());
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        if (session.getStartTime() != null && session.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未到上课时间，无法核销");
        }

        booking.setStatus("USED");
        booking.setUsedAt(LocalDateTime.now());
        coachCourseBookingMapper.updateById(booking);

        int amount = booking.getAmount() == null ? 0 : booking.getAmount().intValue();
        if (amount > 0) {
            walletService.credit(coachUserId, amount, "COACH_COURSE_EARNING", "course earning", "COACH_COURSE_BOOKING", booking.getId());
        }

        userMessageService.createMessage(booking.getUserId(), "COURSE_BOOKING", "课程核销成功", "你的课程已核销成功，祝你运动愉快。", "COACH_COURSE_BOOKING", booking.getId());
        return toDetail(booking);

        // 注意：这里结束的是“教练端核销”的方法体。
        // 之前由于缺失右大括号，导致 staffVerify 方法被错误地嵌套进来，从而触发编译错误。
    }

    @Transactional
    public CourseBookingDetailResponse staffVerify(AuthenticatedUser principal, CourseBookingVerifyRequest request) {
        // 工作人员端：代核销。
        //
        // 与教练核销相比：
        // - staff 不需要校验“课程是否属于自己”，因为 staff 是平台角色
        // - 但仍必须校验订单状态=PAID、上课时间等，避免错误核销
        //
        // 一致性关键点（仍然和教练核销一样）：
        // - 订单 PAID -> USED 与“给教练入账（COACH_COURSE_EARNING）”必须在同一事务中完成
        // - 任一步失败都要回滚，避免出现“已核销但未入账/已入账但未核销”的对账灾难
        requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getVerificationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationCode required");
        }

        String bookingNo = request.getBookingNo();
        String verificationCode = request.getVerificationCode().trim();
        if (!StringUtils.hasText(verificationCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationCode required");
        }

        CoachCourseBooking booking = null;
        if (StringUtils.hasText(bookingNo)) {
            // staff 与教练端一致：优先使用业务唯一的 bookingNo 定位。
            booking = coachCourseBookingMapper.selectOne(
                new LambdaQueryWrapper<CoachCourseBooking>().eq(CoachCourseBooking::getBookingNo, bookingNo.trim())
            );
        } else {
            // staff 的“核销码查单”更严格：额外限定 status=PAID。
            // - 减少误伤（例如输入了一个曾经存在但已取消/已核销的码）
            // - 同时如果出现多条 PAID 同码，仍认为歧义，拒绝核销
            //
            // 这里的设计取舍：
            // - verificationCode 本质是“便于线下输入”的短码，不保证全局唯一
            // - 因此只要存在歧义，就要求改用 bookingNo（更长但唯一）来精确核销
            List<CoachCourseBooking> rows = coachCourseBookingMapper.selectList(
                new LambdaQueryWrapper<CoachCourseBooking>()
                    .eq(CoachCourseBooking::getVerificationCode, verificationCode)
                    .eq(CoachCourseBooking::getStatus, "PAID")
            );
            if (rows == null || rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
            }
            if (rows.size() > 1) {
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
            // 只允许核销 PAID：
            // - 防止重复核销（USED 再核销会重复入账）
            // - 防止未支付订单被核销（造成“白嫖 + 教练错误入账”）
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking not payable");
        }

        CoachCourseSession session = booking.getCourseSessionId() == null ? null : coachCourseSessionMapper.selectById(booking.getCourseSessionId());
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        if (session.getStartTime() != null && session.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未到上课时间，无法核销");
        }

        booking.setStatus("USED");
        booking.setUsedAt(LocalDateTime.now());
        coachCourseBookingMapper.updateById(booking);

        int amount = booking.getAmount() == null ? 0 : booking.getAmount().intValue();
        if (amount > 0 && booking.getCourseSessionId() != null) {
            // staff 核销时，也要给教练入账。
            // 注意这里重新查 course/coachUserId，是因为 staff 入口没有 coach 归属信息。
            //
            // 这段“通过 session 反查 course -> coach”的链路，与教练端 requireCoachCourseByBookingAndCoach 的链路类似：
            // - 教练端用于做权限校验
            // - staff 端用于找到收款方（教练钱包）
            Long courseId = session.getCourseId();
            CoachCourse course = courseId == null ? null : coachCourseMapper.selectById(courseId);
            Long coachUserId = course == null ? null : course.getCoachUserId();
            if (coachUserId != null) {
                walletService.credit(coachUserId, amount, "COACH_COURSE_EARNING", "course earning", "COACH_COURSE_BOOKING", booking.getId());
            }
        }

        userMessageService.createMessage(booking.getUserId(), "COURSE_BOOKING", "课程核销成功", "你的课程已核销成功，祝你运动愉快。", "COACH_COURSE_BOOKING", booking.getId());
        return toDetail(booking);
    }

    public CourseBookingPageResponse myBookings(AuthenticatedUser principal, Integer page, Integer size, String status) {
        // 用户端列表入口：只允许查看自己的订单。
        // status 支持：
        // - 单值：PAID
        // - 或逗号分隔多值：PAID,USED（由 normalizeStatuses 统一做 trim/去空/去重）
        Long userId = requireUserId(principal);
        return listByUser(page, size, userId, status);
    }

    public CourseBookingDetailResponse myBookingDetail(AuthenticatedUser principal, Long id) {
        // 用户端详情入口：只允许查看自己的订单详情。
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachCourseBooking booking = coachCourseBookingMapper.selectById(id);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!Objects.equals(booking.getUserId(), userId)) {
            // 细粒度资源校验：用户只能查看自己的订单详情。
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return toDetail(booking);
    }

    public CourseBookingPageResponse coachBookings(AuthenticatedUser principal, Integer page, Integer size, String status, String bookingNo) {
        // 教练端列表入口：只允许查看自己的课程订单。
        // 参数归一化：
        // - page/size 兜底与上限（防止前端传入过大导致全表扫/大对象返回）
        // - status/bookingNo 作为可选过滤条件，最终由 Mapper 的 JOIN SQL 约束“只能看自己课程的订单”
        Long coachUserId = requireUserId(principal);

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

        // 先 count 再查分页：
        // - total 用于前端分页组件渲染
        // - 当 offset >= total 时无需查 rows，避免无意义 SQL
        long total = coachCourseBookingMapper.countCoachBookings(coachUserId, status, bookingNo);
        long offset = (long) (p - 1) * s;

        List<CourseBookingListItem> items = new ArrayList<>();
        if (offset < total) {
            List<CoachCourseBooking> rows = coachCourseBookingMapper.selectCoachBookingsPage(coachUserId, status, bookingNo, offset, s);
            items = toListItems(rows);
        }

        CourseBookingPageResponse resp = new CourseBookingPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private CourseBookingPageResponse listByUser(Integer page, Integer size, Long userId, String status) {
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

        LambdaQueryWrapper<CoachCourseBooking> countQw = new LambdaQueryWrapper<CoachCourseBooking>().eq(CoachCourseBooking::getUserId, userId);
        List<String> statuses = normalizeStatuses(status);
        if (statuses != null && !statuses.isEmpty()) {
            countQw.in(CoachCourseBooking::getStatus, statuses);
        }
        long total = coachCourseBookingMapper.selectCount(countQw);
        long offset = (long) (p - 1) * s;

        List<CoachCourseBooking> rows = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachCourseBooking> listQw = new LambdaQueryWrapper<CoachCourseBooking>().eq(CoachCourseBooking::getUserId, userId);
            if (statuses != null && !statuses.isEmpty()) {
                listQw.in(CoachCourseBooking::getStatus, statuses);
            }
            listQw.orderByDesc(CoachCourseBooking::getId).last("LIMIT " + s + " OFFSET " + offset);
            rows = coachCourseBookingMapper.selectList(listQw);
        }

        CourseBookingPageResponse resp = new CourseBookingPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(toListItems(rows));
        return resp;
    }

    private List<CourseBookingListItem> toListItems(List<CoachCourseBooking> rows) {
        // 列表 DTO 组装（反范式化）：
        // - 订单表只存 userId/sessionId/amount/status 等
        // - 列表页展示需要：用户名、课程标题、场馆名、教练名、上课时间等
        //
        // 这里采用“批量查询 + Map 缓存”的方式，避免对每一行都 select 一次（N+1）。
        //
        // 这段代码的核心思路：
        // 1) 先把 rows 中涉及到的外键 id（userId/sessionId）批量收集出来
        // 2) 分别 batch select 回来，组装成 Map<id, entity>
        // 3) 最后循环 rows 时只做 Map 查找，整体复杂度从 N 次 DB IO 降到常数次 DB IO
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> userIds = rows.stream().map(CoachCourseBooking::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> sessionIds = rows.stream().map(CoachCourseBooking::getCourseSessionId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, SysUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            if (users != null) {
                for (SysUser u : users) {
                    if (u != null && u.getId() != null) {
                        userMap.put(u.getId(), u);
                    }
                }
            }
        }

        Map<Long, CoachCourseSession> sessionMap = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            List<CoachCourseSession> sessions = coachCourseSessionMapper.selectBatchIds(sessionIds);
            if (sessions != null) {
                for (CoachCourseSession s : sessions) {
                    if (s != null && s.getId() != null) {
                        sessionMap.put(s.getId(), s);
                    }
                }
            }
        }

        Set<Long> courseIds = sessionMap.values().stream().map(CoachCourseSession::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CoachCourse> courseMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            List<CoachCourse> courses = coachCourseMapper.selectBatchIds(courseIds);
            if (courses != null) {
                for (CoachCourse c : courses) {
                    if (c != null && c.getId() != null) {
                        courseMap.put(c.getId(), c);
                    }
                }
            }
        }

        Set<Long> coachUserIds = courseMap.values().stream().map(CoachCourse::getCoachUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> coachNameMap = new HashMap<>();
        if (!coachUserIds.isEmpty()) {
            List<SysUser> coaches = sysUserMapper.selectBatchIds(coachUserIds);
            if (coaches != null) {
                for (SysUser u : coaches) {
                    if (u != null && u.getId() != null) {
                        coachNameMap.put(u.getId(), u.getUsername());
                    }
                }
            }
        }

        Set<Long> venueIds = courseMap.values().stream().map(CoachCourse::getVenueId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> venueNameMap = new HashMap<>();
        if (!venueIds.isEmpty()) {
            List<Venue> venues = venueMapper.selectBatchIds(venueIds);
            if (venues != null) {
                for (Venue v : venues) {
                    if (v != null && v.getId() != null) {
                        venueNameMap.put(v.getId(), v.getName());
                    }
                }
            }
        }

        Set<Long> bookingIds = rows.stream().map(CoachCourseBooking::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> reviewedBookingIds = new HashSet<>();
        if (!bookingIds.isEmpty()) {
            // reviewed 回填：通过评价表 course_review 判断该 booking 是否已评价。
            // 这里同样用批量查询 + Set 存在性判断，避免对每个订单 selectCount 一次。
            List<CourseReview> reviews = courseReviewMapper.selectList(new LambdaQueryWrapper<CourseReview>()
                .in(CourseReview::getBookingId, bookingIds)
            );
            if (reviews != null) {
                for (CourseReview r : reviews) {
                    if (r != null && r.getBookingId() != null) {
                        reviewedBookingIds.add(r.getBookingId());
                    }
                }
            }
        }

        List<CourseBookingListItem> items = new ArrayList<>();
        for (CoachCourseBooking b : rows) {
            // 逐条拼装：允许关联对象为空（例如数据被删除/历史数据不完整），因此每一步都做 null 保护。
            CourseBookingListItem item = new CourseBookingListItem();
            item.setId(b.getId());
            item.setBookingNo(b.getBookingNo());
            item.setUserId(b.getUserId());
            SysUser u = b.getUserId() == null ? null : userMap.get(b.getUserId());
            item.setUsername(u == null ? null : u.getUsername());

            CoachCourseSession s = b.getCourseSessionId() == null ? null : sessionMap.get(b.getCourseSessionId());
            Long courseId = s == null ? null : s.getCourseId();
            CoachCourse c = courseId == null ? null : courseMap.get(courseId);

            item.setCourseId(courseId);
            item.setCourseTitle(c == null ? null : c.getTitle());
            item.setCourseCoverUrl(c == null ? null : c.getCoverUrl());
            item.setCoachUsername(c == null || c.getCoachUserId() == null ? null : coachNameMap.get(c.getCoachUserId()));
            item.setVenueName(c == null || c.getVenueId() == null ? null : venueNameMap.get(c.getVenueId()));
            item.setSessionId(b.getCourseSessionId());
            item.setStartTime(s == null ? null : s.getStartTime());
            item.setEndTime(s == null ? null : s.getEndTime());
            item.setAmount(b.getAmount());
            item.setStatus(b.getStatus());
            item.setVerificationCode(b.getVerificationCode());
            item.setReviewed(b.getId() != null && reviewedBookingIds.contains(b.getId()));
            item.setCoachDecisionAt(b.getCoachDecisionAt());
            item.setRejectReason(b.getRejectReason());
            item.setPaidAt(b.getPaidAt());
            item.setUsedAt(b.getUsedAt());
            item.setCreatedAt(b.getCreatedAt());
            items.add(item);
        }
        return items;
    }

    private CourseBookingDetailResponse toDetail(CoachCourseBooking booking) {
        // 详情 DTO 组装。
        //
        // 与列表类似，这里会补齐用户/课程/教练/场馆等信息，方便前端直接渲染详情页。
        //
        // 注意：这里是“点开单条详情”的场景，因此实现上允许使用少量单条 select（比如按 id 查 user/course）。
        // 如果未来详情页也出现性能瓶颈，可以考虑改造成一次 join 查询或做本地缓存。
        if (booking == null) {
            return null;
        }

        CourseBookingDetailResponse resp = new CourseBookingDetailResponse();
        resp.setId(booking.getId());
        resp.setBookingNo(booking.getBookingNo());
        resp.setUserId(booking.getUserId());
        if (booking.getUserId() != null) {
            SysUser u = sysUserMapper.selectById(booking.getUserId());
            resp.setUsername(u == null ? null : u.getUsername());
        }

        CoachCourseSession session = booking.getCourseSessionId() == null ? null : coachCourseSessionMapper.selectById(booking.getCourseSessionId());
        resp.setSessionId(booking.getCourseSessionId());
        resp.setStartTime(session == null ? null : session.getStartTime());
        resp.setEndTime(session == null ? null : session.getEndTime());

        Long courseId = session == null ? null : session.getCourseId();
        resp.setCourseId(courseId);
        if (courseId != null) {
            CoachCourse c = coachCourseMapper.selectById(courseId);
            resp.setCourseTitle(c == null ? null : c.getTitle());
            resp.setCourseCoverUrl(c == null ? null : c.getCoverUrl());
            if (c != null && c.getVenueId() != null) {
                Venue v = venueMapper.selectById(c.getVenueId());
                resp.setVenueName(v == null ? null : v.getName());
            }
            if (c != null && c.getCoachUserId() != null) {
                SysUser coach = sysUserMapper.selectById(c.getCoachUserId());
                resp.setCoachUsername(coach == null ? null : coach.getUsername());
            }
        }

        resp.setAmount(booking.getAmount());
        resp.setStatus(booking.getStatus());
        resp.setVerificationCode(booking.getVerificationCode());
        boolean reviewed = false;
        if (booking.getId() != null) {
            // 详情页的 reviewed 判断目前采用 selectCount：
            // - 单条详情请求频率较低，简单实现即可
            // - 与列表页不同，列表必须批量优化以避免 N+1
            long cnt = courseReviewMapper.selectCount(new LambdaQueryWrapper<CourseReview>().eq(CourseReview::getBookingId, booking.getId()));
            reviewed = cnt > 0;
        }
        resp.setReviewed(reviewed);
        resp.setCoachDecisionAt(booking.getCoachDecisionAt());
        resp.setRejectReason(booking.getRejectReason());
        resp.setPaidAt(booking.getPaidAt());
        resp.setUsedAt(booking.getUsedAt());
        resp.setCreatedAt(booking.getCreatedAt());
        return resp;
    }

    private List<String> normalizeStatuses(String status) {
        // 把 status 参数归一化为“去空、trim、去重”的列表。
        // 允许前端用 "PAID,USED" 这种形式一次筛选多个状态。
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String s = status.trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        if (s.contains(",")) {
            // 多值：逗号分隔。
            return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        }
        return List.of(s);
    }

    private CoachCourse requireCoachCourseByBookingAndCoach(CoachCourseBooking booking, Long coachUserId) {
        // “教练归属校验”工具方法：给定 booking，推导出其对应的 course，并校验该 course 属于当前教练。
        //
        // 这是教练端接口的核心安全边界：
        // - 教练只能接单/拒单/核销自己的课程订单
        // - 不能通过传入任意 bookingId 来操作别人的订单
        if (booking == null || booking.getCourseSessionId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        CoachCourseSession session = coachCourseSessionMapper.selectById(booking.getCourseSessionId());
        if (session == null || session.getCourseId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        CoachCourse course = coachCourseMapper.selectById(session.getCourseId());
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        if (!Objects.equals(course.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return course;
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
