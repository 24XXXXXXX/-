package com.communitysport.adminuser.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.adminuser.dto.AdminUserCreateRequest;
import com.communitysport.adminuser.dto.AdminUserDetailResponse;
import com.communitysport.adminuser.dto.AdminUserListItem;
import com.communitysport.adminuser.dto.AdminUserPageResponse;
import com.communitysport.adminuser.dto.AdminUserPasswordResetRequest;
import com.communitysport.adminuser.dto.AdminUserRolesUpdateRequest;
import com.communitysport.adminuser.dto.AdminUserStatusUpdateRequest;
import com.communitysport.adminuser.dto.AdminUserUpdateRequest;
import com.communitysport.adminuser.dto.StaffProfileUpdateRequest;
import com.communitysport.auth.entity.SysRole;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.entity.SysUserRole;
import com.communitysport.auth.mapper.SysRoleMapper;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.auth.mapper.SysUserRoleMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.staff.entity.StaffProfile;
import com.communitysport.staff.mapper.StaffProfileMapper;
import com.communitysport.wallet.service.WalletService;

@Service
public class AdminUserService {

    private static final Set<String> ALLOWED_ROLE_CODES = Set.of("ADMIN", "STAFF", "USER", "COACH");

    // 管理端用户管理核心服务：
    // - 提供用户的分页查询、详情、创建、更新、启停用、重置密码、分配角色、维护员工资料等能力
    //
    // 关键设计点：
    // - roleCodes 做白名单：避免任意字符串写入 role 表导致权限口径被污染
    // - 部分操作放在事务中：保证“用户主表 + 角色关系表 + staff_profile + 钱包账户初始化”一致
    // - 角色变更使用 upsert（差量插入 + 多余删除），达到幂等效果

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final StaffProfileMapper staffProfileMapper;

    private final PasswordEncoder passwordEncoder;

    private final WalletService walletService;

    public AdminUserService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            StaffProfileMapper staffProfileMapper,
            PasswordEncoder passwordEncoder,
            WalletService walletService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.staffProfileMapper = staffProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
    }

    public AdminUserPageResponse list(Integer page, Integer size, String keyword, Integer status, String role) {
        // 管理端用户分页查询：
        // - keyword：支持 username/nickname/phone/email 模糊匹配
        // - status：可选（0=禁用，1=启用）
        // - role：可选；提供“按角色筛选”的能力
        //
        // 说明：当 role 过滤存在时，为了避免在用户表上做复杂关联，这里走“角色关系表 -> userIds -> batch 查询用户”的路径。
        // 这种方式的好处：
        // - 角色过滤逻辑清晰
        // - count 与 page 查询可以复用 mapper 的 SQL
        int p = page == null ? 1 : page.intValue();
        int s = size == null ? 20 : size.intValue();
        if (p < 1) {
            p = 1;
        }
        if (s < 1) {
            s = 1;
        }
        if (s > 100) {
            s = 100;
        }

        String roleCode = StringUtils.hasText(role) ? role.trim().toUpperCase(Locale.ROOT) : null;
        if (roleCode != null && !ALLOWED_ROLE_CODES.contains(roleCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role invalid");
        }

        long total;
        List<SysUser> users;
        long offset = (long) (p - 1) * s;

        if (roleCode != null) {
            // 路径 A：按角色筛选
            // - 先查 roleId
            // - 再通过 user_role 中间表统计/分页取 userId
            // - 最后批量查用户详情
            SysRole r = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, roleCode));
            if (r == null || r.getId() == null) {
                total = 0;
                users = List.of();
            } else {
                total = sysUserRoleMapper.countUserIdsByRoleIdFiltered(r.getId(), status, normalizeKeyword(keyword));
                if (offset >= total) {
                    users = List.of();
                } else {
                    List<Long> ids = sysUserRoleMapper.selectUserIdsByRoleIdPageFiltered(r.getId(), status, normalizeKeyword(keyword), offset, s);
                    users = ids == null || ids.isEmpty() ? List.of() : sysUserMapper.selectBatchIds(ids);
                }
            }
        } else {
            // 路径 B：不按角色筛选（只按 keyword/status）
            // - 直接在 sys_user 上 count + page 查询
            LambdaQueryWrapper<SysUser> countQw = buildUserQuery(keyword, status);
            total = sysUserMapper.selectCount(countQw);

            if (offset >= total) {
                users = List.of();
            } else {
                LambdaQueryWrapper<SysUser> listQw = buildUserQuery(keyword, status)
                    .orderByDesc(SysUser::getId)
                    .last("LIMIT " + s + " OFFSET " + offset);
                users = sysUserMapper.selectList(listQw);
            }
        }

        List<AdminUserListItem> items = new ArrayList<>();
        for (SysUser u : users) {
            if (u == null) {
                continue;
            }
            // 这里每条用户都会查询一次 roleCodes（可能形成 N+1）。
            // 当前列表页规模通常不大（且 size 上限 100），因此保留原实现不改动逻辑；
            // 若未来需要优化，可改为一次性批量查询 userId->roles 的映射。
            List<String> roles = sysRoleMapper.selectRoleCodesByUserId(u.getId());

            AdminUserListItem it = new AdminUserListItem();
            it.setId(u.getId());
            it.setUsername(u.getUsername());
            it.setNickname(u.getNickname());
            it.setPhone(u.getPhone());
            it.setEmail(u.getEmail());
            it.setAvatarUrl(u.getAvatarUrl());
            it.setStatus(u.getStatus());
            it.setLastLoginAt(u.getLastLoginAt());
            it.setCreatedAt(u.getCreatedAt());
            it.setRoles(roles);
            items.add(it);
        }

        AdminUserPageResponse resp = new AdminUserPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public AdminUserDetailResponse detail(Long id) {
        // 管理端查看用户详情：
        // - 附带 roles（展示权限）
        // - 附带 staff_profile（若该用户为 STAFF）
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        SysUser u = sysUserMapper.selectById(id);
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(u.getId());
        StaffProfile staff = staffProfileMapper.selectById(u.getId());

        AdminUserDetailResponse resp = new AdminUserDetailResponse();
        resp.setId(u.getId());
        resp.setUsername(u.getUsername());
        resp.setNickname(u.getNickname());
        resp.setPhone(u.getPhone());
        resp.setEmail(u.getEmail());
        resp.setAvatarUrl(u.getAvatarUrl());
        resp.setStatus(u.getStatus());
        resp.setLastLoginAt(u.getLastLoginAt());
        resp.setCreatedAt(u.getCreatedAt());
        resp.setUpdatedAt(u.getUpdatedAt());
        resp.setRoles(roles);
        resp.setStaffRealName(staff == null ? null : staff.getRealName());
        resp.setStaffDepartment(staff == null ? null : staff.getDepartment());
        resp.setStaffPosition(staff == null ? null : staff.getPosition());
        resp.setStaffRegion(staff == null ? null : staff.getRegion());
        return resp;
    }

    @Transactional
    public AdminUserDetailResponse create(AuthenticatedUser principal, AdminUserCreateRequest request) {
        // 创建用户（事务）：
        // - 写 sys_user
        // - 初始化钱包账户（walletService.ensureAccountInitialized）
        // - 写入角色关系（sys_user_role）
        // - 若包含 STAFF 角色，则创建/更新 staff_profile
        requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username/password required");
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();
        if (username.length() < 3 || username.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username length must be 3-50");
        }
        if (password.length() < 6 || password.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password length must be 6-50");
        }

        if (sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username already exists");
        }

        String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        if (phone != null) {
            if (sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone already exists");
            }
        }

        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;
        if (email != null) {
            if (sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email)) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email already exists");
            }
        }

        Integer st = request.getStatus();
        if (st == null) {
            st = 1;
        }
        if (st != 0 && st != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : null);
        user.setPhone(phone);
        user.setEmail(email);
        user.setAvatarUrl(StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl().trim() : null);
        user.setStatus(st);
        user.setLastLoginAt(null);

        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username already exists");
        }

        if (user.getId() == null) {
            SysUser again = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (again == null || again.getId() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user");
            }
            user = again;
        }

        walletService.ensureAccountInitialized(user.getId());

        List<String> roles = request.getRoleCodes();
        if (roles == null || roles.isEmpty()) {
            roles = List.of("USER");
        }
        upsertUserRoles(user.getId(), roles);

        boolean isStaff = roles.stream().filter(Objects::nonNull)
            .map(r -> r.trim().toUpperCase(Locale.ROOT))
            .anyMatch(r -> Objects.equals(r, "STAFF"));

        if (isStaff) {
            upsertStaffProfile(user.getId(), request.getStaffRealName(), request.getStaffDepartment(), request.getStaffPosition(), request.getStaffRegion());
        }

        return detail(user.getId());
    }

    @Transactional
    public AdminUserDetailResponse update(AuthenticatedUser principal, Long id, AdminUserUpdateRequest request) {
        // 更新用户基本信息（事务）：
        // - 这里采用“字段为 null 表示不更新”的写法（见后续对 phone/email/nickname/avatarUrl 的判断）
        // - phone/email 做唯一性检查，避免触发数据库唯一索引异常
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        if (request.getPhone() != null) {
            String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
            if (phone != null) {
                SysUser exists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone)
                    .ne(SysUser::getId, user.getId()));
                if (exists != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone already exists");
                }
            }
            user.setPhone(phone);
        }

        if (request.getEmail() != null) {
            String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;
            if (email != null) {
                SysUser exists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, email)
                    .ne(SysUser::getId, user.getId()));
                if (exists != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email already exists");
                }
            }
            user.setEmail(email);
        }

        if (request.getNickname() != null) {
            user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : null);
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl().trim() : null);
        }

        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return detail(id);
    }

    @Transactional
    public AdminUserDetailResponse updateStatus(AuthenticatedUser principal, Long id, AdminUserStatusUpdateRequest request) {
        // 启用/禁用用户（事务）：
        // - status 约定只允许 0/1
        // - 不在这里级联删除其它业务数据，保持“账号状态”与业务数据解耦
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }

        int st = request.getStatus().intValue();
        if (st != 0 && st != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
        }

        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        user.setStatus(st);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return detail(id);
    }

    @Transactional
    public AdminUserDetailResponse resetPassword(AuthenticatedUser principal, Long id, AdminUserPasswordResetRequest request) {
        // 重置密码（事务）：
        // - 直接更新 password_hash
        // - 密码明文不落库，使用 PasswordEncoder
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword required");
        }

        String pwd = request.getNewPassword();
        if (pwd.length() < 6 || pwd.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword length must be 6-50");
        }

        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        user.setPasswordHash(passwordEncoder.encode(pwd));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return detail(id);
    }

    @Transactional
    public AdminUserDetailResponse updateRoles(AuthenticatedUser principal, Long id, AdminUserRolesUpdateRequest request) {
        // 更新用户角色（事务）：
        // - upsertUserRoles 负责幂等（差量插入 + 删除多余关系）
        // - 若移除 STAFF 角色，则删除 staff_profile（保持数据一致性）
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || request.getRoleCodes() == null || request.getRoleCodes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleCodes required");
        }

        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        List<String> roles = request.getRoleCodes();
        upsertUserRoles(id, roles);

        boolean isStaff = roles.stream().filter(Objects::nonNull)
            .map(r -> r.trim().toUpperCase(Locale.ROOT))
            .anyMatch(r -> Objects.equals(r, "STAFF"));
        if (!isStaff) {
            staffProfileMapper.deleteById(id);
        }

        return detail(id);
    }

    @Transactional
    public AdminUserDetailResponse updateStaffProfile(AuthenticatedUser principal, Long id, StaffProfileUpdateRequest request) {
        // 更新员工资料（事务）：
        // - 只有 STAFF 角色用户允许维护 staff_profile
        // - 资料字段允许部分更新（传 null 表示不改）
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(id);
        boolean isStaff = roles != null && roles.stream().anyMatch(r -> Objects.equals(r, "STAFF"));
        if (!isStaff) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not staff");
        }

        upsertStaffProfile(id, request.getRealName(), request.getDepartment(), request.getPosition(), request.getRegion());
        return detail(id);
    }

    private LambdaQueryWrapper<SysUser> buildUserQuery(String keyword, Integer status) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>();
        if (status != null) {
            qw.eq(SysUser::getStatus, status);
        }
        String kw = normalizeKeyword(keyword);
        if (StringUtils.hasText(kw)) {
            qw.and(w -> w.like(SysUser::getUsername, kw)
                .or().like(SysUser::getNickname, kw)
                .or().like(SysUser::getPhone, kw)
                .or().like(SysUser::getEmail, kw));
        }
        return qw;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String kw = keyword.trim();
        return kw.isEmpty() ? null : kw;
    }

    private void upsertUserRoles(Long userId, List<String> roleCodes) {
        // 用户角色 upsert（幂等更新）：
        // 1) 归一化 roleCodes（trim+upper）并做白名单校验
        // 2) 查询已有的 user_role 关系
        // 3) 对缺少的角色关系进行插入（插入时容忍 DuplicateKeyException，确保并发下幂等）
        // 4) 删除“本次不再保留”的角色关系（差量删除）
        if (userId == null) {
            return;
        }

        Set<String> normalized = new HashSet<>();
        for (String rc : roleCodes) {
            if (!StringUtils.hasText(rc)) {
                continue;
            }
            String c = rc.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_ROLE_CODES.contains(c)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role invalid");
            }
            normalized.add(c);
        }
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleCodes required");
        }

        List<SysUserRole> existing = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        Set<Long> existingRoleIds = new HashSet<>();
        if (existing != null) {
            for (SysUserRole ur : existing) {
                if (ur != null && ur.getRoleId() != null) {
                    existingRoleIds.add(ur.getRoleId());
                }
            }
        }

        Map<String, Long> roleIdMap = new HashMap<>();
        List<SysRole> allRoles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getCode, normalized));
        for (SysRole r : allRoles) {
            roleIdMap.put(r.getCode(), r.getId());
        }

        for (String rc : normalized) {
            Long roleId = roleIdMap.get(rc);
            if (roleId == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, rc + " role not initialized");
            }
            if (existingRoleIds.contains(roleId)) {
                continue;
            }
            SysUserRole insert = new SysUserRole();
            insert.setUserId(userId);
            insert.setRoleId(roleId);
            insert.setCreatedAt(LocalDateTime.now());
            try {
                sysUserRoleMapper.insert(insert);
            } catch (DuplicateKeyException ignored) {
            }
        }

        Set<Long> keepRoleIds = new HashSet<>(roleIdMap.values());
        if (existing != null) {
            for (SysUserRole ur : existing) {
                if (ur == null || ur.getId() == null || ur.getRoleId() == null) {
                    continue;
                }
                if (!keepRoleIds.contains(ur.getRoleId())) {
                    sysUserRoleMapper.deleteById(ur.getId());
                }
            }
        }
    }

    private void upsertStaffProfile(Long userId, String realName, String department, String position, String region) {
        // 员工资料 upsert：
        // - 若不存在则 insert
        // - 若存在则按“参数为 null 表示不更新”的规则进行字段合并
        if (userId == null) {
            return;
        }
        StaffProfile sp = staffProfileMapper.selectById(userId);
        LocalDateTime now = LocalDateTime.now();
        if (sp == null) {
            sp = new StaffProfile();
            sp.setUserId(userId);
            sp.setRealName(StringUtils.hasText(realName) ? realName.trim() : null);
            sp.setDepartment(StringUtils.hasText(department) ? department.trim() : null);
            sp.setPosition(StringUtils.hasText(position) ? position.trim() : null);
            sp.setRegion(StringUtils.hasText(region) ? region.trim() : null);
            sp.setCreatedAt(now);
            sp.setUpdatedAt(now);
            staffProfileMapper.insert(sp);
            return;
        }

        sp.setRealName(realName == null ? sp.getRealName() : (StringUtils.hasText(realName) ? realName.trim() : null));
        sp.setDepartment(department == null ? sp.getDepartment() : (StringUtils.hasText(department) ? department.trim() : null));
        sp.setPosition(position == null ? sp.getPosition() : (StringUtils.hasText(position) ? position.trim() : null));
        sp.setRegion(region == null ? sp.getRegion() : (StringUtils.hasText(region) ? region.trim() : null));
        sp.setUpdatedAt(now);
        staffProfileMapper.updateById(sp);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
