package com.communitysport.wallet.dto;

import java.time.LocalDateTime;

/**
 * 钱包余额响应 DTO。
 *
 * <p>对应接口：GET /api/wallet/balance
 * <p>该响应只承载“账户当前状态”，不包含流水明细。
 */
public class WalletBalanceResponse {

    // 当前登录用户 id（钱包账户主键）。
    private Long userId;

    // 当前余额（最小单位整数）。
    private Integer balance;

    // 余额更新时间（用于前端展示“最后更新时间”或排查问题）。
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getBalance() {
        return balance;
    }

    public void setBalance(Integer balance) {
        this.balance = balance;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
