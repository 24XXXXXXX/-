package com.communitysport.signin.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.signin.dto.SigninResponse;
import com.communitysport.signin.dto.SigninStatusResponse;
import com.communitysport.signin.entity.UserSigninLog;
import com.communitysport.signin.mapper.UserSigninLogMapper;
import com.communitysport.wallet.service.WalletService;

/**
 * 签到服务。
 *
 * <p>签到是钱包的一个“典型入账来源”：
 * <p>- 用户每天签到获得 DAILY_REWARD
 * <p>- 连续签到每满 7 天额外奖励 STREAK_7_BONUS
 *
 * <p>实现要点：
 * <p>1）防止重复签到：
 * <p>- 优先用 Redis setIfAbsent(todayKey) 做“当天唯一”控制（性能好）
 * <p>- Redis 不可用时降级查询 DB（user_signin_log）
 * <p>2）连续天数 streak 计算与缓存：
 * <p>- streakKey 保存 "lastDate|streak" 并设置较长 TTL
 * <p>3）发放奖励：
 * <p>- 通过 walletService.credit 入账，同时写入 wallet_transaction 流水（txn_type=SIGNIN）
 * <p>- 这样后续无论是用户钱包页面还是管理员统计，都能追溯到奖励来源
 */
@Service
public class SigninService {

    private static final DateTimeFormatter DATE_KEY_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private static final DateTimeFormatter DATE_VAL_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final int DAILY_REWARD = 100;

    private static final int STREAK_7_BONUS = 300;

    private final StringRedisTemplate redis;

    private final UserSigninLogMapper userSigninLogMapper;

    private final WalletService walletService;

    public SigninService(StringRedisTemplate redis, UserSigninLogMapper userSigninLogMapper, WalletService walletService) {
        this.redis = redis;
        this.userSigninLogMapper = userSigninLogMapper;
        this.walletService = walletService;
    }

    @Transactional
    public SigninResponse signin(AuthenticatedUser principal) {
        // 签到主流程：
        // - 判断今天是否首次签到
        // - 计算连续签到 streak
        // - 插入 user_signin_log（防止重复的最终依据）
        // - 调用钱包入账发放奖励
        Long userId = requireUserId(principal);
        LocalDate today = LocalDate.now(ZONE);

        boolean firstToday = tryMarkSignedToday(userId, today);
        if (!firstToday) {
            // 今天已经签过：直接返回，不重复发放奖励。
            int streak = readStreakFromRedis(userId, today);
            return buildSigninResponse(true, today, streak, 0, 0);
        }

        int streak = computeAndPersistStreak(userId, today);
        int streakBonus = (streak % 7 == 0) ? STREAK_7_BONUS : 0;

        UserSigninLog log = new UserSigninLog();
        log.setUserId(userId);
        log.setSigninDate(today);
        log.setDailyReward(DAILY_REWARD);
        log.setStreakBonus(streakBonus);
        log.setCreatedAt(LocalDateTime.now(ZONE));

        try {
            userSigninLogMapper.insert(log);
        } catch (DuplicateKeyException e) {
            // 极端并发下，可能出现“Redis 标记成功，但 DB 插入被唯一键拦截”的情况。
            // 此时按“已签到”处理即可，避免重复发钱。
            int s = readStreakFromRedis(userId, today);
            return buildSigninResponse(true, today, s, 0, 0);
        }

        int totalReward = DAILY_REWARD + streakBonus;
        // 关键：通过钱包统一入账。
        // - 余额会增加
        // - 同时落一条 wallet_transaction（可审计）
        walletService.credit(userId, totalReward, "SIGNIN", "每日签到奖励", "SIGNIN", log.getId());

        return buildSigninResponse(true, today, streak, DAILY_REWARD, streakBonus);
    }

    public SigninStatusResponse status(AuthenticatedUser principal) {
        Long userId = requireUserId(principal);
        LocalDate today = LocalDate.now(ZONE);

        long todayCount = userSigninLogMapper.selectCount(new LambdaQueryWrapper<UserSigninLog>()
            .eq(UserSigninLog::getUserId, userId)
            .eq(UserSigninLog::getSigninDate, today));

        UserSigninLog last = userSigninLogMapper.selectOne(new LambdaQueryWrapper<UserSigninLog>()
            .eq(UserSigninLog::getUserId, userId)
            .orderByDesc(UserSigninLog::getSigninDate)
            .last("LIMIT 1"));

        int streak = readStreakFromRedis(userId, today);
        String lastDate = last == null || last.getSigninDate() == null ? null : last.getSigninDate().format(DATE_VAL_FMT);

        SigninStatusResponse resp = new SigninStatusResponse();
        resp.setToday(today.format(DATE_VAL_FMT));
        resp.setTodaySigned(todayCount > 0);
        resp.setStreak(streak);
        resp.setLastSigninDate(lastDate);
        return resp;
    }

    private SigninResponse buildSigninResponse(boolean signed, LocalDate today, int streak, int dailyReward, int streakBonus) {
        SigninResponse resp = new SigninResponse();
        resp.setSigned(signed);
        resp.setSigninDate(today.format(DATE_VAL_FMT));
        resp.setStreak(streak);
        resp.setDailyReward(dailyReward);
        resp.setStreakBonus(streakBonus);
        resp.setTotalReward(dailyReward + streakBonus);
        return resp;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null || !StringUtils.hasText(principal.username())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }

    private String streakKey(Long userId) {
        return "signin:streak:" + userId;
    }

    private String todayKey(Long userId, LocalDate today) {
        return "signin:date:" + userId + ":" + DATE_KEY_FMT.format(today);
    }

    private boolean tryMarkSignedToday(Long userId, LocalDate today) {
        // Redis 版“当天已签到”标记：
        // - setIfAbsent 保证同一天只有第一次会成功
        // - 过期时间设为 2 天，避免 Redis 无限增长
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(todayKey(userId, today), "1", Duration.ofDays(2));
            return Boolean.TRUE.equals(ok);
        } catch (RedisConnectionFailureException e) {
            // Redis 不可用时降级：查 DB 是否已有今天的签到记录。
            long cnt = userSigninLogMapper.selectCount(new LambdaQueryWrapper<UserSigninLog>()
                .eq(UserSigninLog::getUserId, userId)
                .eq(UserSigninLog::getSigninDate, today));
            return cnt == 0;
        }
    }

    private int computeAndPersistStreak(Long userId, LocalDate today) {
        // 计算并写入 streakKey：内容为 "lastDate|streak"。
        // 这样无需每次从数据库统计连续天数，性能更好。
        try {
            String raw = redis.opsForValue().get(streakKey(userId));
            int streak = 1;
            if (raw != null && raw.contains("|")) {
                String[] parts = raw.split("\\|", 2);
                LocalDate lastDate = LocalDate.parse(parts[0], DATE_VAL_FMT);
                int prev = Integer.parseInt(parts[1]);
                if (lastDate.equals(today.minusDays(1))) {
                    streak = prev + 1;
                } else if (lastDate.equals(today)) {
                    streak = prev;
                }
            }
            String newVal = today.format(DATE_VAL_FMT) + "|" + streak;
            redis.opsForValue().set(streakKey(userId), newVal, Duration.ofDays(120));
            return streak;
        } catch (Exception e) {
            return 1;
        }
    }

    private int readStreakFromRedis(Long userId, LocalDate today) {
        try {
            String raw = redis.opsForValue().get(streakKey(userId));
            if (raw == null || !raw.contains("|")) {
                return 0;
            }
            String[] parts = raw.split("\\|", 2);
            LocalDate lastDate = LocalDate.parse(parts[0], DATE_VAL_FMT);
            int streak = Integer.parseInt(parts[1]);
            if (lastDate.equals(today)) {
                return streak;
            }
            if (lastDate.equals(today.minusDays(1))) {
                return streak;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
