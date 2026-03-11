package com.communitysport.adminuser.dto;

public class StaffProfileUpdateRequest {

    // 管理端员工资料更新请求 DTO（写模型）：
    // - 仅对 STAFF 角色用户生效（Service 会校验该用户是否为 STAFF）
    // - 支持“部分更新”：字段传 null 表示不修改该字段
    // - region（片区）会影响：员工端日报统计、巡检/投诉等按片区筛选能力

    private String realName;

    private String department;

    private String position;

    private String region;

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
