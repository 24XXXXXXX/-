package com.communitysport.withdraw.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.communitysport.message.service.UserMessageService;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.wallet.dto.WalletBalanceResponse;
import com.communitysport.wallet.service.WalletService;
import com.communitysport.withdraw.dto.WithdrawCreateRequest;
import com.communitysport.withdraw.dto.WithdrawItem;
import com.communitysport.withdraw.dto.WithdrawPageResponse;
import com.communitysport.withdraw.dto.WithdrawProcessRequest;
import com.communitysport.withdraw.entity.CoachWithdrawRequest;
import com.communitysport.withdraw.mapper.CoachWithdrawRequestMapper;

@Service
public class CoachWithdrawService {

    private final CoachWithdrawRequestMapper coachWithdrawRequestMapper;

    private final SysUserMapper sysUserMapper;

    private final WalletService walletService;

    private final UserMessageService userMessageService;

    public CoachWithdrawService(
            CoachWithdrawRequestMapper coachWithdrawRequestMapper,
            SysUserMapper sysUserMapper,
            WalletService walletService,
            UserMessageService userMessageService
    ) {
        this.coachWithdrawRequestMapper = coachWithdrawRequestMapper;
        this.sysUserMapper = sysUserMapper;
        this.walletService = walletService;
        this.userMessageService = userMessageService;
    }

    @Transactional
    public WithdrawItem create(AuthenticatedUser principal, WithdrawCreateRequest request) {
        Long coachUserId = requireUserId(principal);
        if (request == null || request.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount required");
        }
        int amount = request.getAmount().intValue();
        if (amount <= 0 || amount > 999999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be 1-999999");
        }

        WalletBalanceResponse bal = walletService.getBalance(principal);
        int balance = bal == null || bal.getBalance() == null ? 0 : bal.getBalance().intValue();
        if (balance < amount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        CoachWithdrawRequest row = new CoachWithdrawRequest();
        row.setRequestNo(UUID.randomUUID().toString().replace("-", ""));
        row.setCoachUserId(coachUserId);
        row.setAmount(amount);
        row.setStatus("PENDING");
        row.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        row.setRequestedAt(LocalDateTime.now());
        row.setProcessedBy(null);
        row.setProcessedAt(null);
        coachWithdrawRequestMapper.insert(row);

        userMessageService.createMessage(coachUserId, "WITHDRAW", "提现申请已提交", "你的提现申请已提交，等待管理员审核。", "COACH_WITHDRAW_REQUEST", row.getId());

        return toItem(row, principal.username());
    }

    public WithdrawPageResponse myRequests(AuthenticatedUser principal, Integer page, Integer size, String status) {
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

        LambdaQueryWrapper<CoachWithdrawRequest> countQw = new LambdaQueryWrapper<CoachWithdrawRequest>()
            .eq(CoachWithdrawRequest::getCoachUserId, coachUserId);
        if (StringUtils.hasText(status)) {
            countQw.eq(CoachWithdrawRequest::getStatus, status.trim());
        }
        long total = coachWithdrawRequestMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<WithdrawItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachWithdrawRequest> listQw = new LambdaQueryWrapper<CoachWithdrawRequest>()
                .eq(CoachWithdrawRequest::getCoachUserId, coachUserId);
            if (StringUtils.hasText(status)) {
                listQw.eq(CoachWithdrawRequest::getStatus, status.trim());
            }
            listQw.orderByDesc(CoachWithdrawRequest::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<CoachWithdrawRequest> rows = coachWithdrawRequestMapper.selectList(listQw);
            if (rows != null) {
                for (CoachWithdrawRequest r : rows) {
                    items.add(toItem(r, principal.username()));
                }
            }
        }

        WithdrawPageResponse resp = new WithdrawPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public WithdrawPageResponse adminList(Integer page, Integer size, String status, Long coachUserId, String requestNo) {
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

        LambdaQueryWrapper<CoachWithdrawRequest> countQw = buildAdminQuery(status, coachUserId, requestNo);
        long total = coachWithdrawRequestMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<WithdrawItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachWithdrawRequest> listQw = buildAdminQuery(status, coachUserId, requestNo)
                .orderByDesc(CoachWithdrawRequest::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<CoachWithdrawRequest> rows = coachWithdrawRequestMapper.selectList(listQw);

            Map<Long, String> usernameMap = loadUsernames(rows);
            if (rows != null) {
                for (CoachWithdrawRequest r : rows) {
                    items.add(toItem(r, usernameMap.get(r.getCoachUserId())));
                }
            }
        }

        WithdrawPageResponse resp = new WithdrawPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public WithdrawItem approve(Long adminUserId, Long id, WithdrawProcessRequest request) {
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachWithdrawRequest row = coachWithdrawRequestMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(row.getStatus(), "PENDING")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already processed");
        }

        int amount = row.getAmount() == null ? 0 : row.getAmount().intValue();
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid amount");
        }

        walletService.debit(row.getCoachUserId(), amount, "WITHDRAW", "withdraw approved", "COACH_WITHDRAW_REQUEST", id);

        CoachWithdrawRequest upd = new CoachWithdrawRequest();
        upd.setStatus("APPROVED");
        upd.setProcessedBy(adminUserId);
        upd.setProcessedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRemark())) {
            upd.setRemark(request.getRemark().trim());
        }

        int updated = coachWithdrawRequestMapper.update(upd, new LambdaQueryWrapper<CoachWithdrawRequest>()
            .eq(CoachWithdrawRequest::getId, id)
            .eq(CoachWithdrawRequest::getStatus, "PENDING"));
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already processed");
        }

        userMessageService.createMessage(row.getCoachUserId(), "WITHDRAW", "提现审核通过", "你的提现申请已审核通过。", "COACH_WITHDRAW_REQUEST", id);

        CoachWithdrawRequest after = coachWithdrawRequestMapper.selectById(id);
        return toItem(after, loadUsername(after == null ? null : after.getCoachUserId()));
    }

    @Transactional
    public WithdrawItem reject(Long adminUserId, Long id, WithdrawProcessRequest request) {
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachWithdrawRequest row = coachWithdrawRequestMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(row.getStatus(), "PENDING")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already processed");
        }

        CoachWithdrawRequest upd = new CoachWithdrawRequest();
        upd.setStatus("REJECTED");
        upd.setProcessedBy(adminUserId);
        upd.setProcessedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRemark())) {
            upd.setRemark(request.getRemark().trim());
        }

        int updated = coachWithdrawRequestMapper.update(upd, new LambdaQueryWrapper<CoachWithdrawRequest>()
            .eq(CoachWithdrawRequest::getId, id)
            .eq(CoachWithdrawRequest::getStatus, "PENDING"));
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already processed");
        }

        userMessageService.createMessage(row.getCoachUserId(), "WITHDRAW", "提现审核拒绝", "你的提现申请被拒绝。", "COACH_WITHDRAW_REQUEST", id);

        CoachWithdrawRequest after = coachWithdrawRequestMapper.selectById(id);
        return toItem(after, loadUsername(after == null ? null : after.getCoachUserId()));
    }

    private LambdaQueryWrapper<CoachWithdrawRequest> buildAdminQuery(String status, Long coachUserId, String requestNo) {
        LambdaQueryWrapper<CoachWithdrawRequest> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(CoachWithdrawRequest::getStatus, status.trim());
        }
        if (coachUserId != null) {
            qw.eq(CoachWithdrawRequest::getCoachUserId, coachUserId);
        }
        if (StringUtils.hasText(requestNo)) {
            qw.eq(CoachWithdrawRequest::getRequestNo, requestNo.trim());
        }
        return qw;
    }

    private Map<Long, String> loadUsernames(List<CoachWithdrawRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> ids = rows.stream().map(CoachWithdrawRequest::getCoachUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>();
        if (users != null) {
            for (SysUser u : users) {
                if (u != null && u.getId() != null) {
                    map.put(u.getId(), u.getUsername());
                }
            }
        }
        return map;
    }

    private String loadUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? null : u.getUsername();
    }

    private WithdrawItem toItem(CoachWithdrawRequest row, String coachUsername) {
        if (row == null) {
            return null;
        }
        WithdrawItem it = new WithdrawItem();
        it.setId(row.getId());
        it.setRequestNo(row.getRequestNo());
        it.setCoachUserId(row.getCoachUserId());
        it.setCoachUsername(coachUsername);
        it.setAmount(row.getAmount());
        it.setStatus(row.getStatus());
        it.setRemark(row.getRemark());
        it.setRequestedAt(row.getRequestedAt());
        it.setProcessedBy(row.getProcessedBy());
        it.setProcessedAt(row.getProcessedAt());
        return it;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
