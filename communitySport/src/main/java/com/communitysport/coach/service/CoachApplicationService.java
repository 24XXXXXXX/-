package com.communitysport.coach.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysRole;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.entity.SysUserRole;
import com.communitysport.auth.mapper.SysRoleMapper;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.auth.mapper.SysUserRoleMapper;
import com.communitysport.coach.dto.CoachApplicationItem;
import com.communitysport.coach.dto.CoachApplicationPageResponse;
import com.communitysport.coach.dto.CoachApplicationProcessRequest;
import com.communitysport.coach.dto.CoachApplicationSubmitRequest;
import com.communitysport.coach.entity.CoachApplication;
import com.communitysport.coach.entity.CoachProfile;
import com.communitysport.coach.mapper.CoachApplicationMapper;
import com.communitysport.coach.mapper.CoachProfileMapper;
import com.communitysport.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CoachApplicationService {

    private final CoachApplicationMapper coachApplicationMapper;

    private final CoachProfileMapper coachProfileMapper;

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final ObjectMapper objectMapper;

    public CoachApplicationService(
            CoachApplicationMapper coachApplicationMapper,
            CoachProfileMapper coachProfileMapper,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            ObjectMapper objectMapper
    ) {
        this.coachApplicationMapper = coachApplicationMapper;
        this.coachProfileMapper = coachProfileMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CoachApplicationItem submit(AuthenticatedUser principal, CoachApplicationSubmitRequest request) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        String specialty = StringUtils.hasText(request.getSpecialty()) ? request.getSpecialty().trim() : null;
        String intro = StringUtils.hasText(request.getIntro()) ? request.getIntro().trim() : null;

        String certFilesJson = null;
        if (!CollectionUtils.isEmpty(request.getCertFiles())) {
            try {
                certFilesJson = objectMapper.writeValueAsString(request.getCertFiles());
            } catch (JsonProcessingException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "certFiles invalid");
            }
        }

        CoachApplication existing = coachApplicationMapper.selectOne(
            new LambdaQueryWrapper<CoachApplication>().eq(CoachApplication::getUserId, principal.userId())
        );

        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            CoachApplication row = new CoachApplication();
            row.setUserId(principal.userId());
            row.setSpecialty(specialty);
            row.setIntro(intro);
            row.setCertFiles(certFilesJson);
            row.setAuditStatus("PENDING");
            row.setCreatedAt(now);

            try {
                coachApplicationMapper.insert(row);
            } catch (DuplicateKeyException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "application already exists");
            }

            CoachApplication after = coachApplicationMapper.selectOne(
                new LambdaQueryWrapper<CoachApplication>().eq(CoachApplication::getUserId, principal.userId())
            );
            return toItem(after, principal.username());
        }

        if (Objects.equals(existing.getAuditStatus(), "PENDING") || Objects.equals(existing.getAuditStatus(), "APPROVED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "application already submitted");
        }

        CoachApplication upd = new CoachApplication();
        upd.setSpecialty(specialty);
        upd.setIntro(intro);
        upd.setCertFiles(certFilesJson);
        upd.setAuditStatus("PENDING");
        upd.setAuditRemark(null);
        upd.setAuditedBy(null);
        upd.setAuditedAt(null);
        upd.setCreatedAt(now);

        coachApplicationMapper.update(upd, new LambdaQueryWrapper<CoachApplication>().eq(CoachApplication::getId, existing.getId()));

        CoachApplication after = coachApplicationMapper.selectById(existing.getId());
        return toItem(after, principal.username());
    }

    @Transactional
    public CoachApplicationItem myApplication(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        CoachApplication row = coachApplicationMapper.selectOne(
            new LambdaQueryWrapper<CoachApplication>().eq(CoachApplication::getUserId, principal.userId())
        );
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found");
        }
        return toItem(row, principal.username());
    }

    @Transactional
    public CoachApplicationPageResponse adminList(Integer page, Integer size, String status, Long userId) {
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

        LambdaQueryWrapper<CoachApplication> countQw = buildAdminQuery(status, userId);
        long total = coachApplicationMapper.selectCount(countQw);
        long offset = (long) (p - 1) * s;

        List<CoachApplicationItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CoachApplication> listQw = buildAdminQuery(status, userId)
                .orderByDesc(CoachApplication::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<CoachApplication> rows = coachApplicationMapper.selectList(listQw);
            Map<Long, String> usernameMap = loadUsernames(rows);
            for (CoachApplication r : rows) {
                items.add(toItem(r, usernameMap.get(r.getUserId())));
            }
        }

        CoachApplicationPageResponse resp = new CoachApplicationPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public CoachApplicationItem approve(Long adminUserId, Long id, CoachApplicationProcessRequest request) {
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachApplication row = coachApplicationMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found");
        }
        if (!Objects.equals(row.getAuditStatus(), "PENDING")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "application already processed");
        }

        CoachApplication upd = new CoachApplication();
        upd.setAuditStatus("APPROVED");
        upd.setAuditedBy(adminUserId);
        upd.setAuditedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRemark())) {
            upd.setAuditRemark(request.getRemark().trim());
        }

        int updated = coachApplicationMapper.update(
            upd,
            new LambdaQueryWrapper<CoachApplication>()
                .eq(CoachApplication::getId, id)
                .eq(CoachApplication::getAuditStatus, "PENDING")
        );
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "application already processed");
        }

        addCoachRole(row.getUserId());
        ensureCoachProfile(row.getUserId());

        CoachApplication after = coachApplicationMapper.selectById(id);
        return toItem(after, loadUsername(after == null ? null : after.getUserId()));
    }

    @Transactional
    public CoachApplicationItem reject(Long adminUserId, Long id, CoachApplicationProcessRequest request) {
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CoachApplication row = coachApplicationMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found");
        }
        if (!Objects.equals(row.getAuditStatus(), "PENDING")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "application already processed");
        }

        CoachApplication upd = new CoachApplication();
        upd.setAuditStatus("REJECTED");
        upd.setAuditedBy(adminUserId);
        upd.setAuditedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRemark())) {
            upd.setAuditRemark(request.getRemark().trim());
        }

        int updated = coachApplicationMapper.update(
            upd,
            new LambdaQueryWrapper<CoachApplication>()
                .eq(CoachApplication::getId, id)
                .eq(CoachApplication::getAuditStatus, "PENDING")
        );
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "application already processed");
        }

        CoachApplication after = coachApplicationMapper.selectById(id);
        return toItem(after, loadUsername(after == null ? null : after.getUserId()));
    }

    private LambdaQueryWrapper<CoachApplication> buildAdminQuery(String status, Long userId) {
        LambdaQueryWrapper<CoachApplication> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(CoachApplication::getAuditStatus, status);
        }
        if (userId != null) {
            qw.eq(CoachApplication::getUserId, userId);
        }
        return qw;
    }

    private void addCoachRole(Long userId) {
        if (userId == null) {
            return;
        }
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "COACH"));
        if (role == null || role.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "COACH role not initialized");
        }

        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        ur.setCreatedAt(LocalDateTime.now());

        try {
            sysUserRoleMapper.insert(ur);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void ensureCoachProfile(Long userId) {
        if (userId == null) {
            return;
        }

        CoachProfile existing = coachProfileMapper.selectById(userId);
        if (existing != null) {
            return;
        }

        CoachProfile profile = new CoachProfile();
        profile.setUserId(userId);
        profile.setRatingAvg(BigDecimal.ZERO);
        profile.setRatingCount(0);
        profile.setServiceStatus("ACTIVE");
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());

        try {
            coachProfileMapper.insert(profile);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private CoachApplicationItem toItem(CoachApplication row, String username) {
        if (row == null) {
            return null;
        }

        CoachApplicationItem item = new CoachApplicationItem();
        item.setId(row.getId());
        item.setUserId(row.getUserId());
        item.setUsername(username);
        item.setSpecialty(row.getSpecialty());
        item.setIntro(row.getIntro());
        item.setAuditStatus(row.getAuditStatus());
        item.setAuditRemark(row.getAuditRemark());
        item.setAuditedBy(row.getAuditedBy());
        item.setAuditedAt(row.getAuditedAt());
        item.setCreatedAt(row.getCreatedAt());

        if (StringUtils.hasText(row.getCertFiles())) {
            try {
                List<String> list = objectMapper.readValue(row.getCertFiles(), new TypeReference<List<String>>() {});
                item.setCertFiles(list);
            } catch (Exception e) {
                item.setCertFiles(List.of());
            }
        } else {
            item.setCertFiles(List.of());
        }

        return item;
    }

    private Map<Long, String> loadUsernames(List<CoachApplication> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = rows.stream().map(CoachApplication::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>();
        if (users != null) {
            for (SysUser u : users) {
                if (u != null && u.getId() != null) {
                    map.put(u.getId(), u.getUsername());
                }
            }
        }
        return map;
    }

    private String loadUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? null : u.getUsername();
    }
}
