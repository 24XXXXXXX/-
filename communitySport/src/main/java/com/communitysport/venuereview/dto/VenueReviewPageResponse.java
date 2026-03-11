package com.communitysport.venuereview.dto;

import java.util.List;

public class VenueReviewPageResponse {

    private int page;

    private int size;

    private long total;

    private List<VenueReviewListItem> items;

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

    public List<VenueReviewListItem> getItems() {
        return items;
    }

    public void setItems(List<VenueReviewListItem> items) {
        this.items = items;
    }
}
