package com.communitysport.wallet.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 钱包资金流水表（wallet_transaction）映射。
 *
 * <p>这张表是钱包体系的“审计日志（Audit Trail）”：
 * <p>- wallet_account.balance 只告诉你“现在还有多少钱”
 * <p>- wallet_transaction 告诉你“每一次钱是怎么来的/怎么花的”
 *
 * <p>关键字段说明：
 * <p>- txnType：流水类型（TOPUP、SIGNIN、VENUE_BOOKING、VENUE_REFUND、WITHDRAW 等）
 * <p>- direction：IN/OUT 表示入账/出账
 * <p>- amount：金额（通常是最小单位的整数）
 * <p>- refType + refId：把流水与业务单据关联起来（例如某个订单、某个充值申请、某次签到 log）
 * <p>- txnNo：流水号，用于对账/排查问题（比自增 id 更适合对外展示）
 */
@TableName("wallet_transaction")
public class WalletTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("txn_no")
    // 流水号（对外展示/对账用），通常是随机字符串。
    private String txnNo;

    @TableField("user_id")
    // 归属用户。
    private Long userId;

    @TableField("txn_type")
    // 业务类型编码（由各业务模块在调用 WalletService.credit/debit 时传入）。
    private String txnType;

    // IN/OUT。
    private String direction;

    // 金额（整数）。
    private Integer amount;

    @TableField("ref_type")
    // 关联单据类型（可选）：例如 WALLET_TOPUP_REQUEST、SIGNIN、VENUE_BOOKING。
    private String refType;

    @TableField("ref_id")
    // 关联单据 id（可选）。
    private Long refId;

    // 备注（用于前端展示）。
    private String remark;

    @TableField("created_at")
    // 创建时间（发生记账的时间点）。
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxnNo() {
        return txnNo;
    }

    public void setTxnNo(String txnNo) {
        this.txnNo = txnNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
