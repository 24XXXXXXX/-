package com.communitysport.venue.dto;

import java.time.LocalDate;
import java.util.List;

public class VenueTimeslotGenerateResponse {

    private Long venueId;

    private LocalDate date;

    private int createdCount;

    private long total;

    private List<VenueTimeslotItem> items;

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<VenueTimeslotItem> getItems() {
        return items;
    }

    public void setItems(List<VenueTimeslotItem> items) {
        this.items = items;
    }
}
