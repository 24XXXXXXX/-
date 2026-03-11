package com.communitysport.venue.dto;

import java.util.List;

public class VenuePageResponse {

    private int page;

    private int size;

    private long total;

    private List<VenueListItem> items;

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

    public List<VenueListItem> getItems() {
        return items;
    }

    public void setItems(List<VenueListItem> items) {
        this.items = items;
    }
}
