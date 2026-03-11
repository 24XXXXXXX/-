package com.communitysport.wallet.service;

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
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.wallet.dto.WalletTopupCreateRequest;
import com.communitysport.wallet.dto.WalletTopupProcessRequest;
import com.communitysport.wallet.dto.WalletTopupRequestItem;
import com.communitysport.wallet.dto.WalletTopupRequestPageResponse;
import com.communitysport.wallet.entity.WalletTopupRequest;
import com.communitysport.wallet.mapper.WalletTopupRequestMapper;

/**
 * 充值申请服务（管理员审核发放）。
 *
 * <p>需求口径（见系统需求）：
 * <p>- 用户/教练/员工可提交充值申请（金额 1~9999）
 * <p>- 管理员审核通过后，系统把金额“发放到钱包余额”
 * <p>- 同时写入 wallet_transaction 资金流水（txn_type=TOPUP, direction=IN）
 *
 * <p>为什么要做“申请 + 审核”而不是直接充值？
 * <p>- 这是一个“模拟线下充值/财务审核”的业务流程
 * <p>- 可以练习审核流、状态机与资金入账的一致性
 */
@Service
public class WalletTopupService {

    private final WalletTopupRequestMapper walletTopupRequestMapper;

    private final SysUserMapper sysUserMapper;

    private final WalletService walletService;

    public WalletTopupService(WalletTopupRequestMapper walletTopupRequestMapper, SysUserMapper sysUserMapper, WalletService walletService) {
        this.walletTopupRequestMapper = walletTopupRequestMapper;
        this.sysUserMapper = sysUserMapper;
        this.walletService = walletService;
    }

    @Transactional
    public WalletTopupRequestItem create(AuthenticatedUser principal, WalletTopupCreateRequest request) {
        // 用户提交充值申请：
        // - 只创建一条 WalletTopupRequest 记录（PENDING）
        // - 不直接改钱包余额
        // 钱包余额变动发生在管理员 approve 时。
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (request == null || request.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount required");
        }
        int amount = request.getAmount().intValue();
        if (amount <= 0 || amount > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be 1-9999");
        }

        WalletTopupRequest row = new WalletTopupRequest();
        // requestNo：申请编号，用于前端展示与查询。
        row.setRequestNo(UUID.randomUUID().toString().replace("-", ""));
        row.setUserId(principal.userId());
        row.setAmount(amount);
        row.setStatus("PENDING");
        if (StringUtils.hasText(request.getRemark())) {
            row.setRemark(request.getRemark().trim());
        }
        row.setRequestedAt(LocalDateTime.now());

        walletTopupRequestMapper.insert(row);

        return toItem(row, principal.username());
    }

    @Transactional
    public WalletTopupRequestPageResponse myRequests(AuthenticatedUser principal, Integer page, Integer size, String status) {
        // 查询“我的充值申请”列表（分页）。
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

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

        LambdaQueryWrapper<WalletTopupRequest> countQw = new LambdaQueryWrapper<WalletTopupRequest>()
            .eq(WalletTopupRequest::getUserId, principal.userId());
        if (StringUtils.hasText(status)) {
            countQw.eq(WalletTopupRequest::getStatus, status);
        }
        long total = walletTopupRequestMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<WalletTopupRequestItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<WalletTopupRequest> listQw = new LambdaQueryWrapper<WalletTopupRequest>()
                .eq(WalletTopupRequest::getUserId, principal.userId());
            if (StringUtils.hasText(status)) {
                listQw.eq(WalletTopupRequest::getStatus, status);
            }
            listQw.orderByDesc(WalletTopupRequest::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<WalletTopupRequest> rows = walletTopupRequestMapper.selectList(listQw);
            for (WalletTopupRequest r : rows) {
                items.add(toItem(r, principal.username()));
            }
        }

        WalletTopupRequestPageResponse resp = new WalletTopupRequestPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public WalletTopupRequestPageResponse adminList(Integer page, Integer size, String status, Long userId, String requestNo) {
        // 管理员侧列表：支持按状态/用户/申请编号筛选。
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

        LambdaQueryWrapper<WalletTopupRequest> countQw = buildAdminQuery(status, userId, requestNo);
        long total = walletTopupRequestMapper.selectCount(countQw);
        long offset = (long) (p - 1) * s;

        List<WalletTopupRequestItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<WalletTopupRequest> listQw = buildAdminQuery(status, userId, requestNo)
                .orderByDesc(WalletTopupRequest::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<WalletTopupRequest> rows = walletTopupRequestMapper.selectList(listQw);
            Map<Long, String> usernameMap = loadUsernames(rows);
            for (WalletTopupRequest r : rows) {
                items.add(toItem(r, usernameMap.get(r.getUserId())));
            }
        }

        WalletTopupRequestPageResponse resp = new WalletTopupRequestPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public WalletTopupRequestItem approve(Long adminUserId, Long id, WalletTopupProcessRequest request) {
        // 管理员“通过”充值申请：
        // 1）校验申请存在且状态为 PENDING
        // 2）把申请更新为 APPROVED，并记录 processedBy/processedAt
        // 3）调用 WalletService.credit 给用户钱包入账，并写一条 TOPUP 流水
        //
        // 注意：状态更新使用“where id=? and status=PENDING”的条件更新，
        // 避免并发重复审批。
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        WalletTopupRequest row = walletTopupRequestMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topup request not found");
        }
        if (!Objects.equals(row.getStatus(), "PENDING")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topup request already processed");
        }

        WalletTopupRequest upd = new WalletTopupRequest();
        upd.setStatus("APPROVED");
        upd.setProcessedBy(adminUserId);
        upd.setProcessedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRemark())) {
            upd.setRemark(request.getRemark().trim());
        }

        int updated = walletTopupRequestMapper.update(
            upd,
            new LambdaQueryWrapper<WalletTopupRequest>()
                .eq(WalletTopupRequest::getId, id)
                .eq(WalletTopupRequest::getStatus, "PENDING")
        );
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topup request already processed");
        }

        // 发放到钱包：这里会改变 wallet_account.balance，并插入 wallet_transaction。
        walletService.credit(row.getUserId(), row.getAmount() == null ? 0 : row.getAmount(), "TOPUP", "wallet topup", "WALLET_TOPUP_REQUEST", id);

        WalletTopupRequest after = walletTopupRequestMapper.selectById(id);
        return toItem(after, loadUsername(after == null ? null : after.getUserId()));
    }

    @Transactional
    public WalletTopupRequestItem reject(Long adminUserId, Long id, WalletTopupProcessRequest request) {
        // 管理员“拒绝”充值申请：
        // - 只更新申请状态为 REJECTED，不发生任何资金变动。
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        WalletTopupRequest row = walletTopupRequestMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topup request not found");
        }
        if (!Objects.equals(row.getStatus(), "PENDING")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topup request already processed");
        }

        WalletTopupRequest upd = new WalletTopupRequest();
        upd.setStatus("REJECTED");
        upd.setProcessedBy(adminUserId);
        upd.setProcessedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRemark())) {
            upd.setRemark(request.getRemark().trim());
        }

        int updated = walletTopupRequestMapper.update(
            upd,
            new LambdaQueryWrapper<WalletTopupRequest>()
                .eq(WalletTopupRequest::getId, id)
                .eq(WalletTopupRequest::getStatus, "PENDING")
        );
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topup request already processed");
        }

        WalletTopupRequest after = walletTopupRequestMapper.selectById(id);
        return toItem(after, loadUsername(after == null ? null : after.getUserId()));
    }

    private LambdaQueryWrapper<WalletTopupRequest> buildAdminQuery(String status, Long userId, String requestNo) {
        LambdaQueryWrapper<WalletTopupRequest> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(WalletTopupRequest::getStatus, status);
        }
        if (userId != null) {
            qw.eq(WalletTopupRequest::getUserId, userId);
        }
        if (StringUtils.hasText(requestNo)) {
            qw.eq(WalletTopupRequest::getRequestNo, requestNo);
        }
        return qw;
    }

    private WalletTopupRequestItem toItem(WalletTopupRequest row, String username) {
        if (row == null) {
            return null;
        }
        WalletTopupRequestItem item = new WalletTopupRequestItem();
        item.setId(row.getId());
        item.setRequestNo(row.getRequestNo());
        item.setUserId(row.getUserId());
        item.setUsername(username);
        item.setAmount(row.getAmount());
        item.setStatus(row.getStatus());
        item.setRemark(row.getRemark());
        item.setRequestedAt(row.getRequestedAt());
        item.setProcessedBy(row.getProcessedBy());
        item.setProcessedAt(row.getProcessedAt());
        return item;
    }

    private Map<Long, String> loadUsernames(List<WalletTopupRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = rows.stream().map(WalletTopupRequest::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
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
}
