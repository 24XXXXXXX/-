package com.communitysport.video.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class VideoStaticResourceConfig implements WebMvcConfigurer {

    // 静态资源映射配置：
    // - 目标：让上传到本地磁盘的文件能够被浏览器通过 URL 直接访问
    // - uploadDir 指向项目内的 static/upload 目录（开发/演示环境常用）
    //
    // 重要安全提示：
    // - 这类“本地磁盘直出”通常只适用于小项目/教学演示
    // - 生产环境更常见的是对象存储（OSS/S3）或专门的媒体服务器/CDN
    // - 对于付费视频，即使资源可被访问，仍应避免把真实 videoUrl 直接返回给未购买用户
    //   （本项目通过 Service 决定是否下发 videoUrl 来做第一道控制）

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /upload/** 直接映射到 upload 根目录（包含图片/视频等子目录）
        Path uploadDir = Paths.get("src", "main", "resources", "static", "upload").toAbsolutePath().normalize();
        String uploadLocation = uploadDir.toUri().toString();
        registry.addResourceHandler("/upload/**")
            .addResourceLocations(uploadLocation);

        // /video/** 额外映射到 upload/video 子目录
        // - 兼容一些前端可能用 /video/xxx 的路径访问
        Path videoDir = uploadDir.resolve("video").toAbsolutePath().normalize();
        String videoLocation = videoDir.toUri().toString();
        registry.addResourceHandler("/video/**")
            .addResourceLocations(videoLocation);
    }
}
