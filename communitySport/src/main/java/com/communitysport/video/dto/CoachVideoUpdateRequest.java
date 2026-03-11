package com.communitysport.video.dto;

public class CoachVideoUpdateRequest {

    // 教练编辑视频请求 DTO（写模型）：
    // - 采用“部分更新”语义：字段为 null 表示不修改该字段
    // - 是否为本人视频（coachUserId 匹配）等安全校验在 Service 完成

    private String title;

    private String category;

    private Integer price;

    private String coverUrl;

    private String videoUrl;

    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
