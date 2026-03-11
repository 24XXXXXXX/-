package com.communitysport.complaint.dto;

import java.util.List;

public class ComplaintPageResponse {

    // 投诉分页响应 DTO：
    // - page/size：当前页与每页大小（服务端做边界收敛后回传）
    // - total：符合筛选条件的总条数（用于前端计算总页数）
    // - items：当前页列表项

    private Integer page;

    private Integer size;

    private Long total;

    private List<ComplaintListItem> items;

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

    public List<ComplaintListItem> getItems() {
        return items;
    }

    public void setItems(List<ComplaintListItem> items) {
        this.items = items;
    }
}
