package com.communitysport.booking.dto;

import java.util.List;

/**
 * 场地预约订单分页响应。
 *
 * <p>通用分页结构：page/size/total/items。
 */
public class BookingPageResponse {

    private int page;

    private int size;

    private long total;

    private List<BookingListItem> items;

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

    public List<BookingListItem> getItems() {
        return items;
    }

    public void setItems(List<BookingListItem> items) {
        this.items = items;
    }
}
