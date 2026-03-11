package com.communitysport.video.dto;

public class CoachVideoCreateRequest {

    // 教练新建视频请求 DTO（写模型）：
    // - title/videoUrl 为核心字段（Service 中要求必填）
    // - videoUrl 通常来自 /api/coach/videos/upload 上传接口返回
    // - category/coverUrl/description 可选
    // - price 为空时在 Service 中默认 0（免费）

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
