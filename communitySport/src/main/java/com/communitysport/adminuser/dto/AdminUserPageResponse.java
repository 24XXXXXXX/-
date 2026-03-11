package com.communitysport.adminuser.dto;

import java.util.List;

public class AdminUserPageResponse {

    private int page;

    private int size;

    private long total;

    private List<AdminUserListItem> items;

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

    public List<AdminUserListItem> getItems() {
        return items;
    }

    public void setItems(List<AdminUserListItem> items) {
        this.items = items;
    }
}
