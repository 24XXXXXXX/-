package com.communitysport.wallet.dto;

import java.util.List;

/**
 * 钱包流水分页响应 DTO。
 *
 * <p>对应接口：GET /api/wallet/transactions?page=1&size=20
 * <p>分页约定：
 * <p>- page 从 1 开始
 * <p>- size 为每页大小
 * <p>- total 为总条数
 */
public class WalletTransactionPageResponse {

    // 当前页码（从 1 开始）。
    private int page;

    // 每页大小。
    private int size;

    // 总条数。
    private long total;

    // 当前页的流水列表。
    private List<WalletTransactionItem> items;

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

    public List<WalletTransactionItem> getItems() {
        return items;
    }

    public void setItems(List<WalletTransactionItem> items) {
        this.items = items;
    }
}
