package com.communitysport.coursebooking.dto;

import java.util.List;

public class CourseBookingPageResponse {

    private Integer page;

    // 当前页码（从 1 开始）。

    private Integer size;

    // 每页大小（服务端会做上限保护，例如最大 100）。

    private Long total;

    // 满足筛选条件的总记录数（用于前端分页器展示总页数/总条数）。

    private List<CourseBookingListItem> items;

    // 当前页的数据列表。

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

    public List<CourseBookingListItem> getItems() {
        return items;
    }

    public void setItems(List<CourseBookingListItem> items) {
        this.items = items;
    }
}
