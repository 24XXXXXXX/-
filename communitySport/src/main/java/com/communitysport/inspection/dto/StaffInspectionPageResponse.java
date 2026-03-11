package com.communitysport.inspection.dto;

import java.util.List;

public class StaffInspectionPageResponse {

    // 巡检上报分页响应 DTO：
    // - page/size：当前页与每页大小（服务端会做边界收敛）
    // - total：符合筛选条件的总条数
    // - items：当前页数据

    private Integer page;

    private Integer size;

    private Long total;

    private List<StaffInspectionListItem> items;

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

    public List<StaffInspectionListItem> getItems() {
        return items;
    }

    public void setItems(List<StaffInspectionListItem> items) {
        this.items = items;
    }
}
