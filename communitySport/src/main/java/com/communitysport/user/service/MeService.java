package com.communitysport.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysRoleMapper;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.upload.service.UploadService;
import com.communitysport.user.dto.ChangePasswordRequest;
import com.communitysport.user.dto.MeResponse;
import com.communitysport.user.dto.MeUpdateRequest;
import com.communitysport.user.dto.UserAddressCreateRequest;
import com.communitysport.user.dto.UserAddressItem;
import com.communitysport.user.dto.UserAddressUpdateRequest;
import com.communitysport.user.entity.UserAddress;
import com.communitysport.user.mapper.UserAddressMapper;

@Service
public class MeService {

    private static final String ADMIN_PLACEHOLDER_HASH = "CHANGE_ME_BCRYPT";

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final PasswordEncoder passwordEncoder;

    private final UserAddressMapper userAddressMapper;

    private final UploadService uploadService;

    public MeService(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper, PasswordEncoder passwordEncoder, UserAddressMapper userAddressMapper, UploadService uploadService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.userAddressMapper = userAddressMapper;
        this.uploadService = uploadService;
    }

    public MeResponse getMe(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null || !StringUtils.hasText(principal.username())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        SysUser user = sysUserMapper.selectById(principal.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(user.getId());

        MeResponse resp = new MeResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setPhone(user.getPhone());
        resp.setEmail(user.getEmail());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setStatus(user.getStatus());
        resp.setLastLoginAt(user.getLastLoginAt());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setUpdatedAt(user.getUpdatedAt());
        resp.setRoles(roleCodes);
        return resp;
    }

    public MeResponse updateMe(AuthenticatedUser principal, MeUpdateRequest request) {
        SysUser user = requireActiveUser(principal);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        String nickname = StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : null;
        String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;
        String avatarUrl = StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl().trim() : null;

        if (phone != null) {
            SysUser exists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(SysUser::getId, user.getId()));
            if (exists != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone already exists");
            }
        }

        if (email != null) {
            SysUser exists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .ne(SysUser::getId, user.getId()));
            if (exists != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email already exists");
            }
        }

        boolean changed = false;
        if (request.getNickname() != null) {
            user.setNickname(nickname);
            changed = true;
        }
        if (request.getPhone() != null) {
            user.setPhone(phone);
            changed = true;
        }
        if (request.getEmail() != null) {
            user.setEmail(email);
            changed = true;
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(avatarUrl);
            changed = true;
        }

        if (changed) {
            user.setUpdatedAt(LocalDateTime.now());
            sysUserMapper.updateById(user);
        }

        return getMe(principal);
    }

    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        SysUser user = requireActiveUser(principal);
        if (request == null || !StringUtils.hasText(request.getOldPassword()) || !StringUtils.hasText(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oldPassword/newPassword required");
        }

        String newPassword = request.getNewPassword();
        if (newPassword.length() < 6 || newPassword.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword length must be 6-50");
        }

        if (!verifyPassword(user.getPasswordHash(), request.getOldPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "old password incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    @Transactional
    public String uploadAvatar(AuthenticatedUser principal, MultipartFile file) {
        SysUser user = requireActiveUser(principal);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }

        String url = uploadService.uploadPhoto("avatar", file).getUrl();
        user.setAvatarUrl(url);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return url;
    }

    public List<UserAddressItem> myAddresses(AuthenticatedUser principal) {
        SysUser user = requireActiveUser(principal);
        List<UserAddress> rows = userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
            .eq(UserAddress::getUserId, user.getId())
            .orderByDesc(UserAddress::getIsDefault)
            .orderByDesc(UserAddress::getUpdatedAt));
        return rows.stream().map(this::toItem).toList();
    }

    @Transactional
    public UserAddressItem createAddress(AuthenticatedUser principal, UserAddressCreateRequest request) {
        SysUser user = requireActiveUser(principal);
        if (request == null || !StringUtils.hasText(request.getReceiverName()) || !StringUtils.hasText(request.getReceiverPhone())
                || !StringUtils.hasText(request.getDetail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverName/receiverPhone/detail required");
        }

        Integer isDefault = request.getIsDefault();
        if (isDefault == null) {
            isDefault = 0;
        }

        if (isDefault != 1) {
            Long cnt = userAddressMapper.selectCount(new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, user.getId()));
            if (cnt == null || cnt == 0) {
                isDefault = 1;
            }
        }

        if (isDefault == 1) {
            userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .set(UserAddress::getIsDefault, 0)
                .eq(UserAddress::getUserId, user.getId()));
        }

        LocalDateTime now = LocalDateTime.now();
        UserAddress row = new UserAddress();
        row.setUserId(user.getId());
        row.setReceiverName(request.getReceiverName().trim());
        row.setReceiverPhone(request.getReceiverPhone().trim());
        row.setProvince(StringUtils.hasText(request.getProvince()) ? request.getProvince().trim() : null);
        row.setCity(StringUtils.hasText(request.getCity()) ? request.getCity().trim() : null);
        row.setDistrict(StringUtils.hasText(request.getDistrict()) ? request.getDistrict().trim() : null);
        row.setDetail(request.getDetail().trim());
        row.setIsDefault(isDefault);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        userAddressMapper.insert(row);

        return toItem(row);
    }

    @Transactional
    public UserAddressItem updateAddress(AuthenticatedUser principal, Long id, UserAddressUpdateRequest request) {
        SysUser user = requireActiveUser(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        if (request.getReceiverName() != null && !StringUtils.hasText(request.getReceiverName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverName required");
        }
        if (request.getReceiverPhone() != null && !StringUtils.hasText(request.getReceiverPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverPhone required");
        }
        if (request.getDetail() != null && !StringUtils.hasText(request.getDetail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "detail required");
        }

        UserAddress row = userAddressMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!user.getId().equals(row.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        Integer isDefault = request.getIsDefault();
        if (isDefault != null && isDefault == 1) {
            userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .set(UserAddress::getIsDefault, 0)
                .eq(UserAddress::getUserId, user.getId()));
        }

        if (request.getReceiverName() != null) {
            row.setReceiverName(StringUtils.hasText(request.getReceiverName()) ? request.getReceiverName().trim() : null);
        }
        if (request.getReceiverPhone() != null) {
            row.setReceiverPhone(StringUtils.hasText(request.getReceiverPhone()) ? request.getReceiverPhone().trim() : null);
        }
        if (request.getProvince() != null) {
            row.setProvince(StringUtils.hasText(request.getProvince()) ? request.getProvince().trim() : null);
        }
        if (request.getCity() != null) {
            row.setCity(StringUtils.hasText(request.getCity()) ? request.getCity().trim() : null);
        }
        if (request.getDistrict() != null) {
            row.setDistrict(StringUtils.hasText(request.getDistrict()) ? request.getDistrict().trim() : null);
        }
        if (request.getDetail() != null) {
            row.setDetail(StringUtils.hasText(request.getDetail()) ? request.getDetail().trim() : null);
        }
        if (isDefault != null) {
            row.setIsDefault(isDefault == 1 ? 1 : 0);
        }

        row.setUpdatedAt(LocalDateTime.now());
        userAddressMapper.updateById(row);
        return toItem(row);
    }

    @Transactional
    public void deleteAddress(AuthenticatedUser principal, Long id) {
        SysUser user = requireActiveUser(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        UserAddress row = userAddressMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!user.getId().equals(row.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        userAddressMapper.deleteById(id);

        Long defaultCnt = userAddressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
            .eq(UserAddress::getUserId, user.getId())
            .eq(UserAddress::getIsDefault, 1));
        if (defaultCnt != null && defaultCnt > 0) {
            return;
        }

        List<UserAddress> left = userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
            .eq(UserAddress::getUserId, user.getId())
            .orderByDesc(UserAddress::getId));
        if (left == null || left.isEmpty()) {
            return;
        }
        UserAddress pick = left.get(0);
        userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
            .set(UserAddress::getIsDefault, 1)
            .eq(UserAddress::getId, pick.getId())
            .eq(UserAddress::getUserId, user.getId()));
    }

    private SysUser requireActiveUser(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null || !StringUtils.hasText(principal.username())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        SysUser user = sysUserMapper.selectById(principal.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }

    private boolean verifyPassword(String stored, String raw) {
        if (!StringUtils.hasText(stored) || !StringUtils.hasText(raw)) {
            return false;
        }
        boolean looksBcrypt = stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$") || stored.startsWith("$2$");
        if (looksBcrypt) {
            return passwordEncoder.matches(raw, stored);
        }
        if (ADMIN_PLACEHOLDER_HASH.equals(stored)) {
            return "123456".equals(raw);
        }
        return stored.equals(raw);
    }

    private UserAddressItem toItem(UserAddress row) {
        UserAddressItem it = new UserAddressItem();
        it.setId(row.getId());
        it.setReceiverName(row.getReceiverName());
        it.setReceiverPhone(row.getReceiverPhone());
        it.setProvince(row.getProvince());
        it.setCity(row.getCity());
        it.setDistrict(row.getDistrict());
        it.setDetail(row.getDetail());
        it.setIsDefault(row.getIsDefault());
        it.setCreatedAt(row.getCreatedAt());
        it.setUpdatedAt(row.getUpdatedAt());
        return it;
    }
}
