package com.communitysport.wallet.dto;

import java.util.List;

/**
 * 充值申请分页响应 DTO。
 *
 * <p>对应接口：
 * <p>- GET /api/wallet/topups（用户侧）
 * <p>- GET /api/admin/wallet/topups（管理员侧）
 */
public class WalletTopupRequestPageResponse {

    // 当前页码（从 1 开始）。
    private int page;

    // 每页大小。
    private int size;

    // 总条数。
    private long total;

    // 当前页的申请列表。
    private List<WalletTopupRequestItem> items;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<WalletTopupRequestItem> getItems() {
        return items;
    }

    public void setItems(List<WalletTopupRequestItem> items) {
        this.items = items;
    }
}
