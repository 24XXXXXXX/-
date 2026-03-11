package com.communitysport.wallet.dto;

import java.time.LocalDateTime;

/**
 * 钱包流水项 DTO。
 *
 * <p>对应接口：GET /api/wallet/transactions
 * <p>它是 wallet_transaction 的“对外展示视图”：
 * <p>- 只返回前端需要展示的字段
 * <p>- 不暴露数据库自增 id
 */
public class WalletTransactionItem {

    // 流水号（对外展示/对账用）。
    private String txnNo;

    // 流水类型编码（TOPUP、SIGNIN、VENUE_BOOKING...）。
    private String txnType;

    // IN/OUT（入账/出账）。
    private String direction;

    // 金额（最小单位整数）。
    private Integer amount;

    // 关联单据类型（可选）。
    private String refType;

    // 关联单据 id（可选）。
    private Long refId;

    // 备注（用于前端展示）。
    private String remark;

    // 发生时间。
    private LocalDateTime createdAt;

    public String getTxnNo() {
        return txnNo;
    }

    public void setTxnNo(String txnNo) {
        this.txnNo = txnNo;
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
