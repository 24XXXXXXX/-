package com.communitysport.course.dto;

/**
 * 课程课次状态请求
 */
public class CourseSessionStatusRequest {

    // 课次状态。
    // - 允许值由 service 统一做 trim + 白名单校验（OPEN/CLOSED/CANCELED），避免 controller/前端各自校验导致不一致。
    // - 这里不直接使用枚举类型主要是为了简化前端传参（字符串），但服务端必须强校验以防止脏数据落库。
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
