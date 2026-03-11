package com.communitysport.adminuser.dto;

import java.util.List;

public class AdminUserRolesUpdateRequest {

    // 管理端更新用户角色请求 DTO（写模型）：
    // - roleCodes 为目标角色集合（会覆盖用户现有角色）
    // - 合法角色码由 Service 白名单控制（例如 ADMIN/STAFF/USER/COACH）

    private List<String> roleCodes;

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }
}
