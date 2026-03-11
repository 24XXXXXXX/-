package com.communitysport.video.dto;

import java.util.List;

public class VideoPurchasePageResponse {

    // 购买记录分页响应 DTO：
    // - page/size/total/items 的通用分页结构
    // - items 为 VideoPurchaseListItem（已拼装好展示字段）

    private int page;

    private int size;

    private long total;

    private List<VideoPurchaseListItem> items;

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

    public List<VideoPurchaseListItem> getItems() {
        return items;
    }

    public void setItems(List<VideoPurchaseListItem> items) {
        this.items = items;
    }
}
