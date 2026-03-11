package com.communitysport.favorite.dto;

import java.util.List;

public class FavoritePageResponse {

    private int page;

    private int size;

    private long total;

    private List<FavoriteListItem> items;

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

    public List<FavoriteListItem> getItems() {
        return items;
    }

    public void setItems(List<FavoriteListItem> items) {
        this.items = items;
    }
}
