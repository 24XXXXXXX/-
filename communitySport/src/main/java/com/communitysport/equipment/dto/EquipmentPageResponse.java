package com.communitysport.equipment.dto;

import java.util.List;

public class EquipmentPageResponse {

    // 商品分页响应 DTO：
    // - page/size：当前页码与每页大小（由服务端裁剪后回传，前端可直接回显）
    // - total：符合条件的总记录数（用于前端计算总页数）
    // - items：当前页的数据列表

    private int page;

    private int size;

    private long total;

    private List<EquipmentListItem> items;

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

    public List<EquipmentListItem> getItems() {
        return items;
    }

    public void setItems(List<EquipmentListItem> items) {
        this.items = items;
    }
}
