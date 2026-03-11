package com.communitysport;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口（后端主程序）。
 *
 * <p>这是一个标准的 Spring Boot 项目入口类，主要作用是：
 *
 * <p>1）触发 Spring Boot 自动配置（Auto Configuration），把 Controller / Service / Mapper 等组件装配到容器中。
 * <p>2）配置 MyBatis 的 Mapper 扫描范围，让 MyBatis-Plus 能识别并生成 Mapper 代理对象。
 * <p>3）开启定时任务能力（@EnableScheduling），用于签到奖励、过期处理、统计等“周期性任务”。
 */
@SpringBootApplication
@MapperScan(basePackages = {"com.communitysport"})
@EnableScheduling
public class CommunitySportApplication {

    /**
     * Java 程序真正的 main 函数。
     *
     * <p>SpringApplication.run(...) 会：
     * <p>- 创建 Spring ApplicationContext（容器）
     * <p>- 扫描并注册 Bean（Controller / Service / Mapper 等）
     * <p>- 启动内置 Web 服务器（默认 Tomcat）
     */
    public static void main(String[] args) {
        SpringApplication.run(CommunitySportApplication.class, args);
    }
}
