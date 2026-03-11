package com.communitysport.video.dto;

public class CoachVideoUploadResponse {

    // 上传视频文件后的响应 DTO：
    // - fileName：服务端生成的存储文件名（避免重名、避免信任用户文件名）
    // - videoUrl：可访问的静态资源 URL（通常保存到 CoachVideo.videoUrl 字段）

    private String fileName;

    private String videoUrl;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
}
