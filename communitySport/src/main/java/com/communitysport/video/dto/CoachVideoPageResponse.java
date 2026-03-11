package com.communitysport.video.dto;

import java.util.List;

public class CoachVideoPageResponse {

    // 视频分页响应 DTO：
    // - page/size：当前页与每页大小（服务端做边界收敛后回传）
    // - total：符合条件的总条数
    // - items：当前页列表项

    private int page;

    private int size;

    private long total;

    private List<CoachVideoListItem> items;

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

    public List<CoachVideoListItem> getItems() {
        return items;
    }

    public void setItems(List<CoachVideoListItem> items) {
        this.items = items;
    }
}
