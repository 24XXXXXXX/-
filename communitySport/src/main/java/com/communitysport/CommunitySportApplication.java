package com.communitysport;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口（后端主程序）。
 *
 * <p>这是一个标准的 Spring Boot 项目入口类：
 * <p>- 你运行 main 方法，本质是在启动一个 Spring 容器（ApplicationContext）+ 内置 Web 服务器（Tomcat）。
 * <p>- 后续所有 Controller/Service/Mapper 等组件，都会在启动阶段被扫描、注册为 Bean，并由 Spring 负责依赖注入。
 *
 * <p>本类上几个关键注解的含义：
 * <p>1）@SpringBootApplication：
 * <p>- 等价于 @Configuration + @EnableAutoConfiguration + @ComponentScan 的组合
 * <p>- 触发“自动配置”（Auto Configuration），根据 classpath 里的依赖自动装配常用 Bean（MVC、Jackson、数据源等）
 * <p>- 默认以当前包（com.communitysport）为根进行组件扫描，把标注了 @Controller/@Service/@Component 等的类加入容器
 * <p>
 * <p>2）@MapperScan：
 * <p>- 配置 MyBatis 的 Mapper 扫描范围，让框架能为接口生成动态代理对象
 * <p>- 这样在 Service 中注入 Mapper 接口时（例如 private final XxxMapper），运行期才能正确调用到 SQL
 * <p>
 * <p>3）@EnableScheduling：
 * <p>- 开启 Spring 定时任务能力（识别 @Scheduled）
 * <p>- 常用于签到奖励、过期处理、自动状态流转、统计等“周期性任务”
 *
 * <p>注意：这些注释只解释启动与装配机制，不涉及任何业务逻辑的改变。
 */
@SpringBootApplication
@MapperScan(basePackages = {"com.communitysport"})
@EnableScheduling
public class CommunitySportApplication {

    /**
     * Java 程序真正的 main 函数。
     *
     * <p>SpringApplication.run(...) 会：
     * <p>- 启动 Spring Boot 应用（创建/刷新 ApplicationContext）
     * <p>- 扫描并注册 Bean（Controller / Service / Mapper 等），完成依赖注入
     * <p>- 启动内置 Web 服务器（默认 Tomcat），开始监听端口并对外提供 HTTP API
     */
    public static void main(String[] args) {
        SpringApplication.run(CommunitySportApplication.class, args);
    }
}
