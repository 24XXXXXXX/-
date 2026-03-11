package com.communitysport.equipment.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.equipment.dto.EquipmentCategoryItem;
import com.communitysport.equipment.dto.EquipmentDetailResponse;
import com.communitysport.equipment.dto.EquipmentListItem;
import com.communitysport.equipment.dto.EquipmentPageResponse;
import com.communitysport.equipment.entity.Equipment;
import com.communitysport.equipment.entity.EquipmentCategory;
import com.communitysport.equipment.mapper.EquipmentCategoryMapper;
import com.communitysport.equipment.mapper.EquipmentMapper;
import com.communitysport.upload.service.UploadService;

@Service
public class EquipmentCatalogService {

    private final EquipmentCategoryMapper equipmentCategoryMapper;

    private final EquipmentMapper equipmentMapper;

    private final UploadService uploadService;

    public EquipmentCatalogService(EquipmentCategoryMapper equipmentCategoryMapper, EquipmentMapper equipmentMapper, UploadService uploadService) {
        this.equipmentCategoryMapper = equipmentCategoryMapper;
        this.equipmentMapper = equipmentMapper;
        this.uploadService = uploadService;
    }

    public List<EquipmentCategoryItem> listCategories() {
        // 商品分类列表：
        // - 用于前端筛选栏/下拉框
        // - 这里按 id 升序输出，保证分类展示顺序稳定（当然也可以改成按 name，但本系统沿用 id 顺序）
        List<EquipmentCategory> rows = equipmentCategoryMapper.selectList(
            new LambdaQueryWrapper<EquipmentCategory>().orderByAsc(EquipmentCategory::getId)
        );
        List<EquipmentCategoryItem> items = new ArrayList<>();
        for (EquipmentCategory row : rows) {
            EquipmentCategoryItem item = new EquipmentCategoryItem();
            item.setId(row.getId());
            item.setName(row.getName());
            items.add(item);
        }
        return items;
    }

    public EquipmentPageResponse listEquipments(Integer page, Integer size, Long categoryId, String keyword) {
        // 商品目录分页：
        //
        // 这属于“读模型”查询：
        // - 只返回列表页需要展示的字段（EquipmentListItem）
        // - 强制只展示 ON_SALE（上架可售）商品，避免用户端看到下架数据
        // - 支持 categoryId 精确过滤 + keyword 模糊搜索
        //
        // 分页策略：
        // - 先 count 得到 total
        // - 再根据 page/size 计算 offset，并用 LIMIT/OFFSET 拉取当前页
        // - offset >= total 时直接返回空 items（避免无意义的 selectList）
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

        LambdaQueryWrapper<Equipment> countQw = buildEquipmentQuery(categoryId, keyword);
        long total = equipmentMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<EquipmentListItem> items = new ArrayList<>();

        if (offset < total) {
            LambdaQueryWrapper<Equipment> listQw = buildEquipmentQuery(categoryId, keyword)
                .orderByDesc(Equipment::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<Equipment> rows = equipmentMapper.selectList(listQw);

            // 批量加载分类名称，避免 N+1：
            // - 列表中每个 equipment 都有 categoryId，如果逐条 selectById 会导致 N+1
            // - 这里先把 categoryId 去重为集合，再一次性 selectByIds
            Map<Long, String> categoryNameMap = loadCategoryNames(rows);

            for (Equipment row : rows) {
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

    public EquipmentDetailResponse getEquipment(Long id) {
        // 商品详情（用户端）：
        // - 只允许查看 ON_SALE（上架）商品
        // - OFF_SALE/不存在一律按 NOT_FOUND 处理，避免暴露“下架商品是否存在”的信息
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        Equipment row = equipmentMapper.selectById(id);
        if (row == null || !Objects.equals(row.getStatus(), "ON_SALE")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }

        EquipmentCategory category = row.getCategoryId() == null ? null : equipmentCategoryMapper.selectById(row.getCategoryId());

        EquipmentDetailResponse resp = new EquipmentDetailResponse();
        resp.setId(row.getId());
        resp.setCategoryId(row.getCategoryId());
        resp.setCategoryName(category == null ? null : category.getName());
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

    public String uploadAndUpdateCover(Long id, MultipartFile file) {
        // 上传并更新封面（管理/员工侧）：
        // - Controller 层已做角色校验
        // - Service 侧只关心参数与数据存在性
        //
        // 流程：
        // - 上传图片到对象存储/本地（UploadService）
        // - 拿到 url 回写 equipment.cover_url
        //
        // 注意：这里并不校验 equipment.status（上/下架都允许修改封面），属于后台管理能力。
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }

        Equipment row = equipmentMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }

        String url = uploadService.uploadPhoto("equipment", file).getUrl();
        row.setCoverUrl(url);
        equipmentMapper.updateById(row);
        return url;
    }

    private LambdaQueryWrapper<Equipment> buildEquipmentQuery(Long categoryId, String keyword) {
        // 用户侧查询条件构造：
        // - 强制 status=ON_SALE（避免下架商品出现在目录/搜索结果中）
        // - categoryId/keyword 为可选条件
        LambdaQueryWrapper<Equipment> qw = new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getStatus, "ON_SALE");
        if (categoryId != null) {
            qw.eq(Equipment::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(Equipment::getName, keyword);
        }
        return qw;
    }

    private Map<Long, String> loadCategoryNames(List<Equipment> rows) {
        // 批量把 categoryId -> categoryName 映射出来，供列表 DTO 组装使用。
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
}
