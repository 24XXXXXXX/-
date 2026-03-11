package com.communitysport.equipment.dto;

import java.util.List;

public class AdminOrderPageResponse {

    // 管理端/员工端订单分页响应 DTO：
    // - 结构与用户侧分页一致：page/size/total/items
    // - items 为 AdminOrderListItem（包含更多履约/运营字段）

    private int page;

    private int size;

    private long total;

    private List<AdminOrderListItem> items;

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

    public List<AdminOrderListItem> getItems() {
        return items;
    }

    public void setItems(List<AdminOrderListItem> items) {
        this.items = items;
    }
}
