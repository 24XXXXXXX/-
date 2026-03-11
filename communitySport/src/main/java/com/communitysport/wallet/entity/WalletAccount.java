package com.communitysport.wallet.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 钱包账户表（wallet_account）映射。
 *
 * <p>设计要点：
 * <p>- 一人一户：用 user_id 作为主键（而不是额外生成 account_id）
 * <p>- balance 记录当前余额，属于“状态数据”
 * <p>- 资金流水属于“历史数据”，在 wallet_transaction 表中
 *
 * <p>金额单位：
 * <p>- 这里使用 Integer 存储，通常代表“分/积分/最小货币单位”。
 *   （项目里用 100、300 作为签到奖励，也符合积分式钱包）
 */
@TableName("wallet_account")
public class WalletAccount {

    @TableId(value = "user_id", type = IdType.INPUT)
    // userId 既是用户主键，也是钱包账户主键。
    private Long userId;

    // 当前余额。
    private Integer balance;

    @TableField("updated_at")
    // 余额最后一次更新时间（便于展示/对账/排查问题）。
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
