package com.communitysport.course.dto;

import java.util.List;

public class CoursePageResponse {

    private Integer page;

    private Integer size;

    private Long total;

    private List<CourseListItem> items;

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

    public List<CourseListItem> getItems() {
        return items;
    }

    public void setItems(List<CourseListItem> items) {
        this.items = items;
    }
}
