package com.communitysport.video.dto;

public class CoachVideoStatusRequest {

    // 上下架状态更新请求 DTO：
    // - 仅承载目标状态，避免与“编辑视频信息”混用
    // - status 合法值在 Service 中校验（ON_SALE / OFF_SALE）

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
