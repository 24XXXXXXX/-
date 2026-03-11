package com.communitysport.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("coach_video")
public class CoachVideo {

    // 教练视频主表（写模型）：
    // - 由教练创建并维护的视频元数据
    // - 用户端公开目录/详情主要读取该表（只展示 ON_SALE）
    //
    // 重要字段语义：
    // - price：视频价格（单位与钱包一致，通常为“分”）
    //   - 0 表示免费视频：视为“天然已购买”，用户详情可直接拿到 videoUrl
    // - status：ON_SALE / OFF_SALE
    //   - ON_SALE 才能在用户端公开展示与购买
    // - videoUrl：真实播放地址
    //   - 该字段存储在表中，但是否下发给前端由 Service 控制（未购买时不返回）

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("coach_user_id")
    private Long coachUserId;

    // coachUserId：上传者（教练）的用户 id
    // - 教练端编辑/上下架等操作需要校验“是否本人”

    private String title;

    private String category;

    // category：用于用户端筛选/分类展示；为空时在 Service 中默认“其他”

    private Integer price;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("video_url")
    private String videoUrl;

    private String description;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // createdAt：创建时间
    // - 常用于列表排序（最新）与统计口径

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCoachUserId() {
        return coachUserId;
    }

    public void setCoachUserId(Long coachUserId) {
        this.coachUserId = coachUserId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
