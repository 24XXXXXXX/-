package com.communitysport.adminuser.dto;

import java.util.List;

public class AdminUserCreateRequest {

    // 管理端创建用户请求 DTO（写模型）：
    // - 用于管理员后台“新增账号”功能
    // - 除了 sys_user 的基础字段外，还支持一次性写入角色与（可选）员工资料
    //
    // 关键约束（由 Service 校验）：
    // - username/password 必填且有长度限制
    // - phone/email 若填写需保证唯一
    // - roleCodes 为空时默认赋值为 ["USER"]
    // - staffRealName/department/position/region 仅当角色包含 STAFF 时才会落库到 staff_profile

    private String username;

    private String password;

    private String nickname;

    private String phone;

    private String email;

    private String avatarUrl;

    private Integer status;

    private List<String> roleCodes;

    // 员工资料（可选）：用于创建 STAFF 账号时同步写入 staff_profile

    private String staffRealName;

    private String staffDepartment;

    private String staffPosition;

    private String staffRegion;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }

    public String getStaffRealName() {
        return staffRealName;
    }

    public void setStaffRealName(String staffRealName) {
        this.staffRealName = staffRealName;
    }

    public String getStaffDepartment() {
        return staffDepartment;
    }

    public void setStaffDepartment(String staffDepartment) {
        this.staffDepartment = staffDepartment;
    }

    public String getStaffPosition() {
        return staffPosition;
    }

    public void setStaffPosition(String staffPosition) {
        this.staffPosition = staffPosition;
    }

    public String getStaffRegion() {
        return staffRegion;
    }

    public void setStaffRegion(String staffRegion) {
        this.staffRegion = staffRegion;
    }
}
