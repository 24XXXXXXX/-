package com.communitysport.equipment.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.equipment.dto.AdminEquipmentCreateRequest;
import com.communitysport.equipment.dto.AdminEquipmentUpdateRequest;
import com.communitysport.equipment.dto.EquipmentDetailResponse;
import com.communitysport.equipment.dto.EquipmentListItem;
import com.communitysport.equipment.dto.EquipmentPageResponse;
import com.communitysport.equipment.dto.EquipmentStatusUpdateRequest;
import com.communitysport.equipment.entity.Equipment;
import com.communitysport.equipment.entity.EquipmentCategory;
import com.communitysport.equipment.mapper.EquipmentCategoryMapper;
import com.communitysport.equipment.mapper.EquipmentMapper;
import com.communitysport.security.AuthenticatedUser;

@Service
public class EquipmentAdminService {

    private final EquipmentCategoryMapper equipmentCategoryMapper;

    private final EquipmentMapper equipmentMapper;

    public EquipmentAdminService(EquipmentCategoryMapper equipmentCategoryMapper, EquipmentMapper equipmentMapper) {
        this.equipmentCategoryMapper = equipmentCategoryMapper;
        this.equipmentMapper = equipmentMapper;
    }

    public EquipmentPageResponse list(Integer page, Integer size, Long categoryId, String keyword, String status) {
        // 后台商品列表分页：
        //
        // 与用户侧目录不同点：
        // - 允许看到全部状态（ON_SALE/OFF_SALE），并支持 status 过滤
        // - 允许按 keyword/categoryId 组合查询
        //
        // 分页策略：
        // - 先 count 得到 total
        // - 再根据 page/size 计算 offset，用 LIMIT/OFFSET 拉取当前页
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

        LambdaQueryWrapper<Equipment> countQw = buildQuery(categoryId, keyword, status);
        long total = equipmentMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<EquipmentListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<Equipment> listQw = buildQuery(categoryId, keyword, status)
                .orderByDesc(Equipment::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<Equipment> rows = equipmentMapper.selectList(listQw);

            // 批量加载分类名称，避免 N+1。
            Map<Long, String> categoryNameMap = loadCategoryNames(rows);

            for (Equipment row : rows) {
                if (row == null) {
                    continue;
                }
                EquipmentListItem item = new EquipmentListItem();
                item.setId(row.getId());
                item.setCategoryId(row.getCategoryId());
                item.setCategoryName(categoryNameMap.get(row.getCategoryId()));
                item.setName(row.getName());
                item.setSpec(row.getSpec());
                item.setPurpose(row.getPurpose());
                item.setPrice(row.getPrice());
                item.setStock(row.getStock());
                item.setCoverUrl(row.getCoverUrl());
                item.setStatus(row.getStatus());
                items.add(item);
            }
        }

        EquipmentPageResponse resp = new EquipmentPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public EquipmentDetailResponse detail(Long id) {
        // 后台商品详情：
        // - 不限制 status（上/下架都能看）
        // - id 不存在则 404
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        Equipment row = equipmentMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }

        return toDetail(row);
    }

    @Transactional
    public EquipmentDetailResponse create(AuthenticatedUser principal, AdminEquipmentCreateRequest request) {
        // 后台新增商品：
        // - 由 Controller 控制 ADMIN 权限；Service 仍校验 principal 存在性（防内部调用绕过）
        // - categoryId/name 为必填
        // - price/stock 做非负校验
        // - status 为空默认 ON_SALE（也就是“创建即上架”，符合小型系统的简化逻辑）
        //
        // 这里用 @Transactional：
        // - 虽然当前流程是单表 insert，但事务边界能保证未来扩展（如写操作日志/同步搜索索引）时仍有一致性载体
        requireUserId(principal);
        if (request == null || request.getCategoryId() == null || !StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId/name required");
        }

        EquipmentCategory cat = equipmentCategoryMapper.selectById(request.getCategoryId());
        if (cat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId invalid");
        }

        String name = request.getName().trim();
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name length must be <=100");
        }

        Integer price = request.getPrice() == null ? 0 : request.getPrice().intValue();
        if (price < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price invalid");
        }

        Integer stock = request.getStock() == null ? 0 : request.getStock().intValue();
        if (stock < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock invalid");
        }

        String status = request.getStatus();
        if (!StringUtils.hasText(status)) {
            status = "ON_SALE";
        } else {
            status = status.trim();
            if (!Objects.equals(status, "ON_SALE") && !Objects.equals(status, "OFF_SALE")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
            }
        }

        Equipment row = new Equipment();
        row.setCategoryId(request.getCategoryId());
        row.setName(name);
        row.setSpec(trimToNull(request.getSpec(), 255));
        row.setPurpose(trimToNull(request.getPurpose(), 255));
        row.setPrice(price);
        row.setStock(stock);
        row.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);
        row.setDescription(request.getDescription());
        row.setStatus(status);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        equipmentMapper.insert(row);

        return detail(row.getId());
    }

    @Transactional
    public EquipmentDetailResponse update(AuthenticatedUser principal, Long id, AdminEquipmentUpdateRequest request) {
        // 后台编辑商品：
        // - 采用“部分更新（PATCH 风格）”：字段为 null 表示不修改
        // - 每个字段分别做合法性校验与裁剪（trimToNull）
        // - updatedAt 由服务端统一刷新，避免客户端伪造时间
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        Equipment row = equipmentMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }

        if (request.getCategoryId() != null) {
            EquipmentCategory cat = equipmentCategoryMapper.selectById(request.getCategoryId());
            if (cat == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId invalid");
            }
            row.setCategoryId(request.getCategoryId());
        }
        if (request.getName() != null) {
            if (!StringUtils.hasText(request.getName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
            }
            String name = request.getName().trim();
            if (name.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name length must be <=100");
            }
            row.setName(name);
        }
        if (request.getSpec() != null) {
            row.setSpec(trimToNull(request.getSpec(), 255));
        }
        if (request.getPurpose() != null) {
            row.setPurpose(trimToNull(request.getPurpose(), 255));
        }
        if (request.getPrice() != null) {
            int price = request.getPrice().intValue();
            if (price < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price invalid");
            }
            row.setPrice(price);
        }
        if (request.getStock() != null) {
            int stock = request.getStock().intValue();
            if (stock < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock invalid");
            }
            row.setStock(stock);
        }
        if (request.getCoverUrl() != null) {
            row.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);
        }
        if (request.getDescription() != null) {
            row.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            String st = request.getStatus();
            if (!StringUtils.hasText(st)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
            }
            st = st.trim();
            if (!Objects.equals(st, "ON_SALE") && !Objects.equals(st, "OFF_SALE")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
            }
            row.setStatus(st);
        }

        row.setUpdatedAt(LocalDateTime.now());
        equipmentMapper.updateById(row);
        return detail(id);
    }

    @Transactional
    public EquipmentDetailResponse updateStatus(AuthenticatedUser principal, Long id, EquipmentStatusUpdateRequest request) {
        // 后台上/下架（只更新 status）：
        // - 单独提供接口而不是复用 update，是为了前端“上架/下架按钮”更直观
        // - 只允许 ON_SALE / OFF_SALE
        // - 成功后返回最新详情
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }

        String st = request.getStatus().trim();
        if (!Objects.equals(st, "ON_SALE") && !Objects.equals(st, "OFF_SALE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalid");
        }

        Equipment row = equipmentMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }

        row.setStatus(st);
        row.setUpdatedAt(LocalDateTime.now());
        equipmentMapper.updateById(row);
        return detail(id);
    }

    @Transactional
    public void delete(AuthenticatedUser principal, Long id) {
        // 后台删除商品：
        //
        // 这里用 try/catch 把底层异常统一转换为 409：
        // - 常见冲突原因：存在外键引用/已有订单明细引用该商品，导致无法删除
        // - 对前端而言，409 表示“当前资源状态不允许删除”
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        try {
            equipmentMapper.deleteById(id);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot delete");
        }
    }

    private LambdaQueryWrapper<Equipment> buildQuery(Long categoryId, String keyword, String status) {
        // 后台查询条件构造：全部为可选条件
        LambdaQueryWrapper<Equipment> qw = new LambdaQueryWrapper<Equipment>();
        if (categoryId != null) {
            qw.eq(Equipment::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(Equipment::getName, keyword.trim());
        }
        if (StringUtils.hasText(status)) {
            qw.eq(Equipment::getStatus, status.trim());
        }
        return qw;
    }

    private Map<Long, String> loadCategoryNames(List<Equipment> rows) {
        // 批量把 categoryId -> categoryName 映射出来，用于列表 DTO 组装。
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }

        Set<Long> ids = rows.stream().map(Equipment::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<EquipmentCategory> categories = equipmentCategoryMapper.selectByIds(ids);
        Map<Long, String> map = new HashMap<>();
        for (EquipmentCategory c : categories) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    private EquipmentDetailResponse toDetail(Equipment row) {
        // 组装后台商品详情 DTO：
        // - 这里会额外查一次分类表，拿到 categoryName
        // - 对于“单条详情”场景，这种 1 次额外查询可接受
        EquipmentCategory cat = row.getCategoryId() == null ? null : equipmentCategoryMapper.selectById(row.getCategoryId());

        EquipmentDetailResponse resp = new EquipmentDetailResponse();
        resp.setId(row.getId());
        resp.setCategoryId(row.getCategoryId());
        resp.setCategoryName(cat == null ? null : cat.getName());
        resp.setName(row.getName());
        resp.setSpec(row.getSpec());
        resp.setPurpose(row.getPurpose());
        resp.setPrice(row.getPrice());
        resp.setStock(row.getStock());
        resp.setCoverUrl(row.getCoverUrl());
        resp.setDescription(row.getDescription());
        resp.setStatus(row.getStatus());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    private String trimToNull(String s, int maxLen) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        if (t.length() > maxLen) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "field too long");
        }
        return t;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
