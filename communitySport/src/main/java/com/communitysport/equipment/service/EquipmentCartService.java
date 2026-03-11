package com.communitysport.equipment.service;

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
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.equipment.dto.CartItemResponse;
import com.communitysport.equipment.dto.CartResponse;
import com.communitysport.equipment.dto.CartUpdateRequest;
import com.communitysport.equipment.entity.Equipment;
import com.communitysport.equipment.entity.EquipmentCartItem;
import com.communitysport.equipment.mapper.EquipmentCartItemMapper;
import com.communitysport.equipment.mapper.EquipmentMapper;
import com.communitysport.security.AuthenticatedUser;

@Service
public class EquipmentCartService {

    private final EquipmentCartItemMapper equipmentCartItemMapper;

    private final EquipmentMapper equipmentMapper;

    public EquipmentCartService(EquipmentCartItemMapper equipmentCartItemMapper, EquipmentMapper equipmentMapper) {
        this.equipmentCartItemMapper = equipmentCartItemMapper;
        this.equipmentMapper = equipmentMapper;
    }

    @Transactional
    public CartResponse getCart(AuthenticatedUser principal) {
        // 购物车查询：
        //
        // 这是一个典型的“主表行（cart_item）+ 关联表信息（equipment）”的读模型组装：
        // - 先按 userId 查询出购物车行（每行记录 equipmentId + quantity）
        // - 再批量把 equipmentId 集合查出 equipment 行，构建 map
        // - 最后在内存中拼装 DTO（商品名/封面/单价/小计）
        //
        // 为什么要“先批量查 equipment，再用 map 组装”？
        // - 避免 N+1 查询：如果对每个 cart_item 都 selectById，会导致一次请求打出 N 次 SQL。
        // - 批量查询 + map 是简单但有效的性能优化。
        Long userId = requireUserId(principal);

        List<EquipmentCartItem> rows = equipmentCartItemMapper.selectList(
            new LambdaQueryWrapper<EquipmentCartItem>()
                .eq(EquipmentCartItem::getUserId, userId)
                .orderByDesc(EquipmentCartItem::getId)
        );

        Set<Long> equipmentIds = rows.stream().map(EquipmentCartItem::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Equipment> equipmentMap = new HashMap<>();
        if (!equipmentIds.isEmpty()) {
            // 批量加载商品行（用于展示）。
            // 注意：这里没有强制过滤 status，因为购物车行可能存在“历史遗留”（商品下架/删除）。
            // 最终展示时 equipment 为空，会体现在 DTO 里（name/price 等为 null）。
            List<Equipment> equipments = equipmentMapper.selectByIds(equipmentIds);
            for (Equipment e : equipments) {
                equipmentMap.put(e.getId(), e);
            }
        }

        List<CartItemResponse> items = new ArrayList<>();
        int totalQuantity = 0;
        int totalAmount = 0;
        for (EquipmentCartItem row : rows) {
            Equipment e = row.getEquipmentId() == null ? null : equipmentMap.get(row.getEquipmentId());
            Integer price = e == null ? null : e.getPrice();
            int qty = row.getQuantity() == null ? 0 : row.getQuantity();
            int subtotal = price == null ? 0 : price.intValue() * qty;

            CartItemResponse item = new CartItemResponse();
            item.setId(row.getId());
            item.setEquipmentId(row.getEquipmentId());
            item.setEquipmentName(e == null ? null : e.getName());
            item.setCoverUrl(e == null ? null : e.getCoverUrl());
            item.setPrice(price);
            item.setQuantity(qty);
            item.setSubtotal(subtotal);
            items.add(item);

            totalQuantity += qty;
            totalAmount += subtotal;
        }

        CartResponse resp = new CartResponse();
        resp.setItems(items);
        resp.setTotalQuantity(totalQuantity);
        resp.setTotalAmount(totalAmount);
        return resp;
    }

    @Transactional
    public CartResponse updateCart(AuthenticatedUser principal, CartUpdateRequest request) {
        // 购物车更新（“写模型”）：
        //
        // 这个接口的设计语义接近“PUT/覆盖式更新”：
        // - 给定 equipmentId 与 quantity
        // - quantity <= 0 表示从购物车移除该商品
        // - quantity > 0 表示设置该商品在购物车中的数量为该值
        //
        // 这样设计的好处：
        // - 幂等：同一个请求重复提交，最终购物车状态一致
        // - 便于前端：直接把数量输入框的值回传即可，不需要前端自己维护增量
        Long userId = requireUserId(principal);
        if (request == null || request.getEquipmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "equipmentId required");
        }

        Equipment equipment = equipmentMapper.selectById(request.getEquipmentId());
        if (equipment == null || !Objects.equals(equipment.getStatus(), "ON_SALE")) {
            // 只允许对“上架商品”加入购物车：
            // - 下架/不存在的商品不允许继续被加购
            // - 这里直接返回 404，避免前端继续展示无效商品
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }

        int qty = request.getQuantity() == null ? 0 : request.getQuantity().intValue();
        if (qty < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity invalid");
        }
        if (qty > 999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity too large");
        }

        EquipmentCartItem existing = equipmentCartItemMapper.selectOne(
            new LambdaQueryWrapper<EquipmentCartItem>()
                .eq(EquipmentCartItem::getUserId, userId)
                .eq(EquipmentCartItem::getEquipmentId, request.getEquipmentId())
        );

        if (qty <= 0) {
            // 删除语义：如果存在则删，不存在则当作成功（天然幂等）。
            if (existing != null) {
                equipmentCartItemMapper.deleteById(existing.getId());
            }
            return getCart(principal);
        }

        if (existing != null) {
            // 覆盖更新数量。
            existing.setQuantity(qty);
            equipmentCartItemMapper.updateById(existing);
            return getCart(principal);
        }

        EquipmentCartItem insert = new EquipmentCartItem();
        insert.setUserId(userId);
        insert.setEquipmentId(request.getEquipmentId());
        insert.setQuantity(qty);
        try {
            equipmentCartItemMapper.insert(insert);
        } catch (DuplicateKeyException e) {
            // 并发补偿：
            //
            // 理论上同一 userId + equipmentId 在购物车中应该唯一（通常由数据库唯一索引保证）。
            // 在并发场景下（例如用户连续点击“加入购物车”按钮或网络重试），可能出现：
            // - 两个请求都没查到 existing
            // - 然后同时 insert，后者触发 DuplicateKeyException
            //
            // 这里的处理思路是“读回并覆盖更新”，从而把冲突请求收敛为同一个最终状态。
            EquipmentCartItem again = equipmentCartItemMapper.selectOne(
                new LambdaQueryWrapper<EquipmentCartItem>()
                    .eq(EquipmentCartItem::getUserId, userId)
                    .eq(EquipmentCartItem::getEquipmentId, request.getEquipmentId())
            );
            if (again != null) {
                again.setQuantity(qty);
                equipmentCartItemMapper.updateById(again);
            }
        }

        return getCart(principal);
    }

    @Transactional
    public List<EquipmentCartItem> listCartRows(Long userId) {
        // 下单时使用的“读原始行”方法：
        // - 仅返回 cart_item 行（不组装 DTO）
        // - orderByAsc 保证在生成订单明细时顺序稳定（便于排查问题/对账）
        return equipmentCartItemMapper.selectList(
            new LambdaQueryWrapper<EquipmentCartItem>()
                .eq(EquipmentCartItem::getUserId, userId)
                .orderByAsc(EquipmentCartItem::getId)
        );
    }

    @Transactional
    public void clearCart(Long userId) {
        // 清空购物车：
        // - 通常由下单成功后调用
        // - userId 为空时直接返回，避免误删
        if (userId == null) {
            return;
        }
        equipmentCartItemMapper.delete(new LambdaQueryWrapper<EquipmentCartItem>().eq(EquipmentCartItem::getUserId, userId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
