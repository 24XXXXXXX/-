package com.communitysport.message.dto;

import java.util.List;

public class UserMessagePageResponse {

    private Integer page;

    private Integer size;

    private Long total;

    private List<UserMessageItem> items;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<UserMessageItem> getItems() {
        return items;
    }

    public void setItems(List<UserMessageItem> items) {
        this.items = items;
    }
}
