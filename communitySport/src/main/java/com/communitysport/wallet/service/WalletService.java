package com.communitysport.wallet.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.wallet.dto.WalletBalanceResponse;
import com.communitysport.wallet.dto.WalletTransactionItem;
import com.communitysport.wallet.dto.WalletTransactionPageResponse;
import com.communitysport.wallet.entity.WalletAccount;
import com.communitysport.wallet.entity.WalletTransaction;
import com.communitysport.wallet.mapper.WalletAccountMapper;
import com.communitysport.wallet.mapper.WalletTransactionMapper;

/**
 * 钱包服务（余额 + 资金流水 + 统一记账入口）。
 *
 * <p>在本项目中，“钱包”承担了所有支付能力（替代第三方支付）：
 * <p>- 场地预约支付
 * <p>- 课程预约支付
 * <p>- 器材订单支付
 * <p>- 视频购买支付
 * <p>- 退款、收入、签到奖励、充值发放等
 *
 * <p>核心原则：
 * <p>1）任何“余额变动”必须同时写入一条 wallet_transaction 资金流水（可审计、可追溯）。
 * <p>2）扣款必须是原子操作：避免并发下出现“余额变成负数”或“重复扣款”。
 * <p>3）余额与流水写入需要在同一事务中完成（@Transactional），保证一致性。
 */
@Service
public class WalletService {

    private final WalletAccountMapper walletAccountMapper;

    private final WalletTransactionMapper walletTransactionMapper;

    public WalletService(WalletAccountMapper walletAccountMapper, WalletTransactionMapper walletTransactionMapper) {
        this.walletAccountMapper = walletAccountMapper;
        this.walletTransactionMapper = walletTransactionMapper;
    }

    @Transactional
    public void ensureAccountInitialized(Long userId) {
        // 确保钱包账户存在。
        //
        // 为什么需要“显式初始化”？
        // - 项目里很多业务（支付/退款/奖励）都会触达钱包
        // - 与其让每个业务都处理“account 不存在”的情况，不如统一在 WalletService 内部兜底
        //
        // 典型调用点：
        // - 用户注册/登录时初始化（避免第一次支付才创建导致体验不一致）
        // - 任何涉及资金变动的入口（credit/debit）都会再次 ensureAccount
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        ensureAccount(userId);
    }

    @Transactional
    public WalletBalanceResponse getBalance(AuthenticatedUser principal) {
        // 获取当前登录用户的钱包余额。
        //
        // 注意：
        // - 余额是“账户状态”，流水是“历史记录”，两者都重要
        // - 本接口只返回当前余额，不做统计聚合
        Long userId = requireUserId(principal);

        WalletAccount account = ensureAccount(userId);

        WalletBalanceResponse resp = new WalletBalanceResponse();
        resp.setUserId(userId);
        resp.setBalance(account.getBalance() == null ? 0 : account.getBalance());
        resp.setUpdatedAt(account.getUpdatedAt());
        return resp;
    }

    @Transactional
    public WalletTransactionPageResponse getTransactions(AuthenticatedUser principal, Integer page, Integer size) {
        // 获取当前用户的资金流水（分页）。
        //
        // 分页参数：
        // - page：从 1 开始
        // - size：单页大小（做上限保护，避免一次拉太多）
        //
        // 为什么要做流水分页？
        // - 流水理论上会越来越多，全量返回会越来越慢
        // - 前端也更适合“按页加载 + 无限滚动/分页组件”
        Long userId = requireUserId(principal);

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

        ensureAccount(userId);

        long total = walletTransactionMapper.countByUserId(userId);
        long offset = (long) (p - 1) * s;

        List<WalletTransactionItem> items = new ArrayList<>();
        if (offset < total) {
            List<WalletTransaction> rows = walletTransactionMapper.selectPageByUserId(userId, offset, s);
            for (WalletTransaction row : rows) {
                WalletTransactionItem item = new WalletTransactionItem();
                item.setTxnNo(row.getTxnNo());
                item.setTxnType(row.getTxnType());
                item.setDirection(row.getDirection());
                item.setAmount(row.getAmount());
                item.setRefType(row.getRefType());
                item.setRefId(row.getRefId());
                item.setRemark(row.getRemark());
                item.setCreatedAt(row.getCreatedAt());
                items.add(item);
            }
        }

        WalletTransactionPageResponse resp = new WalletTransactionPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public void credit(Long userId, int amount, String txnType, String remark, String refType, Long refId) {
        // 入账（加余额）：例如充值通过、退款、签到奖励、教练收入等。
        //
        // 这里的实现遵循“余额变动 + 流水落库”的一致性原则：
        // - 先确保账户存在
        // - 再更新余额（SQL: balance = balance + delta）
        // - 最后插入流水记录（direction=IN）
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }

        ensureAccount(userId);

        int updated = walletAccountMapper.addBalance(userId, amount);
        if (updated <= 0) {
            // 正常情况下 addBalance 会更新到 1 行。
            // 如果更新不到，可能是账户不存在（极端并发/初始化时序问题），这里再次 ensure 并重试。
            ensureAccount(userId);
            updated = walletAccountMapper.addBalance(userId, amount);
            if (updated <= 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update wallet balance");
            }
        }

        WalletTransaction txn = new WalletTransaction();
        // txnNo：流水号（用于追踪/对账）。这里用 UUID 简化实现。
        txn.setTxnNo(UUID.randomUUID().toString().replace("-", ""));
        txn.setUserId(userId);
        txn.setTxnType(txnType);
        txn.setDirection("IN");
        txn.setAmount(amount);
        txn.setRefType(refType);
        txn.setRefId(refId);
        txn.setRemark(remark);
        txn.setCreatedAt(LocalDateTime.now());
        walletTransactionMapper.insert(txn);
    }

    @Transactional
    public void debit(Long userId, int amount, String txnType, String remark, String refType, Long refId) {
        // 出账（扣余额）：例如场地预约支付、课程支付、器材订单支付、视频购买等。
        //
        // 扣款的关键点是“防止余额扣成负数”，所以这里使用：
        // UPDATE wallet_account SET balance = balance - ? WHERE user_id = ? AND balance >= ?
        //
        // 该 SQL 是原子条件更新：
        // - 并发情况下只会有一个请求成功扣款
        // - 失败的请求 updated=0，从而返回“余额不足”
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }

        ensureAccount(userId);

        int updated = walletAccountMapper.subtractBalance(userId, amount);
        if (updated <= 0) {
            // updated=0 表示余额不足或账户不存在。
            // 这里优先按“余额不足”提示（更符合用户直觉）。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "余额不够，请先充值");
        }

        WalletTransaction txn = new WalletTransaction();
        txn.setTxnNo(UUID.randomUUID().toString().replace("-", ""));
        txn.setUserId(userId);
        txn.setTxnType(txnType);
        txn.setDirection("OUT");
        txn.setAmount(amount);
        txn.setRefType(refType);
        txn.setRefId(refId);
        txn.setRemark(remark);
        txn.setCreatedAt(LocalDateTime.now());
        walletTransactionMapper.insert(txn);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        // 从 Spring Security 的认证上下文中读取 userId。
        // 如果没有认证信息（未登录），直接返回 401。
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }

    private WalletAccount ensureAccount(Long userId) {
        // 确保钱包账户存在（不存在则创建）。
        //
        // 这里使用 userId 作为主键（wallet_account.user_id），
        // 这样每个用户天然只有 1 个钱包账户。
        WalletAccount account = walletAccountMapper.selectById(userId);
        if (account != null) {
            if (account.getBalance() == null) {
                account.setBalance(0);
            }
            return account;
        }

        WalletAccount insert = new WalletAccount();
        insert.setUserId(userId);
        insert.setBalance(0);
        try {
            walletAccountMapper.insert(insert);
        } catch (DuplicateKeyException ignored) {
            // 并发下可能两个线程同时创建账户：
            // - 其中一个 insert 成功
            // - 另一个会触发唯一键冲突（DuplicateKeyException）
            // 这里忽略异常，然后再查一次即可。
        }

        WalletAccount again = walletAccountMapper.selectById(userId);
        if (again == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to initialize wallet account");
        }
        if (again.getBalance() == null) {
            again.setBalance(0);
        }
        return again;
    }
}
