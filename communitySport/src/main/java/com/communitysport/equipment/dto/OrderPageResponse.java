package com.communitysport.equipment.dto;

import java.util.List;

public class OrderPageResponse {

    // 用户侧订单分页响应 DTO：
    // - 结构与通用分页一致：page/size/total/items
    // - items 为轻量列表项（OrderListItem），详情信息需通过 /orders/{id} 查询

    private int page;

    private int size;

    private long total;

    private List<OrderListItem> items;

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

    public List<OrderListItem> getItems() {
        return items;
    }

    public void setItems(List<OrderListItem> items) {
        this.items = items;
    }
}
