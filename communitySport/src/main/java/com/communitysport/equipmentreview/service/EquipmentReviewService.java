package com.communitysport.equipmentreview.service;

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
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.equipment.entity.Equipment;
import com.communitysport.equipment.entity.EquipmentOrder;
import com.communitysport.equipment.entity.EquipmentOrderItem;
import com.communitysport.equipment.mapper.EquipmentMapper;
import com.communitysport.equipment.mapper.EquipmentOrderItemMapper;
import com.communitysport.equipment.mapper.EquipmentOrderMapper;
import com.communitysport.equipmentreview.dto.EquipmentReviewCreateRequest;
import com.communitysport.equipmentreview.dto.EquipmentReviewListItem;
import com.communitysport.equipmentreview.dto.EquipmentReviewPageResponse;
import com.communitysport.equipmentreview.entity.EquipmentReview;
import com.communitysport.equipmentreview.mapper.EquipmentReviewMapper;
import com.communitysport.security.AuthenticatedUser;

@Service
public class EquipmentReviewService {

    private final EquipmentReviewMapper equipmentReviewMapper;

    private final EquipmentMapper equipmentMapper;

    private final EquipmentOrderMapper equipmentOrderMapper;

    private final EquipmentOrderItemMapper equipmentOrderItemMapper;

    private final SysUserMapper sysUserMapper;

    public EquipmentReviewService(
            EquipmentReviewMapper equipmentReviewMapper,
            EquipmentMapper equipmentMapper,
            EquipmentOrderMapper equipmentOrderMapper,
            EquipmentOrderItemMapper equipmentOrderItemMapper,
            SysUserMapper sysUserMapper
    ) {
        this.equipmentReviewMapper = equipmentReviewMapper;
        this.equipmentMapper = equipmentMapper;
        this.equipmentOrderMapper = equipmentOrderMapper;
        this.equipmentOrderItemMapper = equipmentOrderItemMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Transactional
    public EquipmentReviewListItem create(AuthenticatedUser principal, EquipmentReviewCreateRequest request) {
        Long userId = requireUserId(principal);
        if (request == null || request.getEquipmentId() == null || request.getRating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "equipmentId/rating required");
        }

        Long equipmentId = request.getEquipmentId();
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }

        int rating = request.getRating().intValue();
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating must be 1-5");
        }

        long exists = equipmentReviewMapper.selectCount(
            new LambdaQueryWrapper<EquipmentReview>()
                .eq(EquipmentReview::getEquipmentId, equipmentId)
                .eq(EquipmentReview::getUserId, userId)
        );
        if (exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reviewed");
        }

        ensureUserPurchasedAndReceived(userId, equipmentId);

        String content = request.getContent();
        if (content != null) {
            content = content.trim();
            if (!StringUtils.hasText(content)) {
                content = null;
            }
            if (content != null && content.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content too long");
            }
        }

        EquipmentReview row = new EquipmentReview();
        row.setEquipmentId(equipmentId);
        row.setUserId(userId);
        row.setRating(rating);
        row.setContent(content);
        row.setCreatedAt(LocalDateTime.now());
        equipmentReviewMapper.insert(row);

        EquipmentReview after = row.getId() == null ? null : equipmentReviewMapper.selectById(row.getId());
        return toItem(after);
    }

    public EquipmentReviewPageResponse listByEquipment(Long equipmentId, Integer page, Integer size) {
        if (equipmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "equipmentId required");
        }

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

        LambdaQueryWrapper<EquipmentReview> countQw = new LambdaQueryWrapper<EquipmentReview>()
            .eq(EquipmentReview::getEquipmentId, equipmentId);
        long total = equipmentReviewMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<EquipmentReviewListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<EquipmentReview> listQw = new LambdaQueryWrapper<EquipmentReview>()
                .eq(EquipmentReview::getEquipmentId, equipmentId)
                .orderByDesc(EquipmentReview::getId)
                .last("LIMIT " + s + " OFFSET " + offset);

            List<EquipmentReview> rows = equipmentReviewMapper.selectList(listQw);

            Set<Long> userIds = rows.stream().map(EquipmentReview::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> userMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
                if (users != null) {
                    for (SysUser u : users) {
                        if (u != null && u.getId() != null) {
                            userMap.put(u.getId(), u.getUsername());
                        }
                    }
                }
            }

            for (EquipmentReview r : rows) {
                EquipmentReviewListItem it = new EquipmentReviewListItem();
                it.setId(r.getId());
                it.setEquipmentId(r.getEquipmentId());
                it.setUserId(r.getUserId());
                it.setUsername(r.getUserId() == null ? null : userMap.get(r.getUserId()));
                it.setRating(r.getRating());
                it.setContent(r.getContent());
                it.setCreatedAt(r.getCreatedAt());
                items.add(it);
            }
        }

        EquipmentReviewPageResponse resp = new EquipmentReviewPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private EquipmentReviewListItem toItem(EquipmentReview r) {
        if (r == null) {
            return null;
        }

        EquipmentReviewListItem item = new EquipmentReviewListItem();
        item.setId(r.getId());
        item.setEquipmentId(r.getEquipmentId());
        item.setUserId(r.getUserId());
        if (r.getUserId() != null) {
            SysUser u = sysUserMapper.selectById(r.getUserId());
            item.setUsername(u == null ? null : u.getUsername());
        }
        item.setRating(r.getRating());
        item.setContent(r.getContent());
        item.setCreatedAt(r.getCreatedAt());
        return item;
    }

    private void ensureUserPurchasedAndReceived(Long userId, Long equipmentId) {
        List<EquipmentOrder> orders = equipmentOrderMapper.selectList(
            new LambdaQueryWrapper<EquipmentOrder>()
                .eq(EquipmentOrder::getUserId, userId)
                .eq(EquipmentOrder::getStatus, "RECEIVED")
        );
        if (orders == null || orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no received orders");
        }

        Set<Long> orderIds = orders.stream().map(EquipmentOrder::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no received orders");
        }

        long cnt = equipmentOrderItemMapper.selectCount(
            new LambdaQueryWrapper<EquipmentOrderItem>()
                .in(EquipmentOrderItem::getOrderId, orderIds)
                .eq(EquipmentOrderItem::getEquipmentId, equipmentId)
        );
        if (cnt <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not purchased");
        }
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
