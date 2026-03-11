package com.communitysport.wallet.dto;

/**
 * 创建充值申请请求 DTO。
 *
 * <p>对应接口：POST /api/wallet/topups
 * <p>注意：金额的合法性（例如 1~9999）在 WalletTopupService 中做最终校验。
 */
public class WalletTopupCreateRequest {

    // 申请充值金额（整数，最小单位）。
    private Integer amount;

    // 申请备注（可选）。
    private String remark;

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
