package com.communitysport.wallet.dto;

/**
 * 管理员处理充值申请请求 DTO。
 *
 * <p>对应接口：
 * <p>- POST /api/admin/wallet/topups/{id}/approve
 * <p>- POST /api/admin/wallet/topups/{id}/reject
 */
public class WalletTopupProcessRequest {

    // 管理员处理备注（可选）。
    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
