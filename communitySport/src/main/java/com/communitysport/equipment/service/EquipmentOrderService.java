package com.communitysport.equipment.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.equipment.dto.OrderCreateRequest;
import com.communitysport.equipment.dto.OrderDetailResponse;
import com.communitysport.equipment.dto.OrderItemResponse;
import com.communitysport.equipment.dto.OrderListItem;
import com.communitysport.equipment.dto.OrderPageResponse;
import com.communitysport.equipment.dto.AdminOrderListItem;
import com.communitysport.equipment.dto.AdminOrderPageResponse;
import com.communitysport.equipment.dto.OrderShipRequest;
import com.communitysport.equipment.entity.Equipment;
import com.communitysport.equipment.entity.EquipmentCartItem;
import com.communitysport.equipment.entity.EquipmentOrder;
import com.communitysport.equipment.entity.EquipmentOrderItem;
import com.communitysport.equipment.mapper.EquipmentMapper;
import com.communitysport.equipment.mapper.EquipmentOrderItemMapper;
import com.communitysport.equipment.mapper.EquipmentOrderMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.wallet.service.WalletService;

@Service
public class EquipmentOrderService {

    private final EquipmentOrderMapper equipmentOrderMapper;

    private final EquipmentOrderItemMapper equipmentOrderItemMapper;

    private final SysUserMapper sysUserMapper;

    private final EquipmentMapper equipmentMapper;

    private final EquipmentCartService equipmentCartService;

    private final WalletService walletService;

    public EquipmentOrderService(
            EquipmentOrderMapper equipmentOrderMapper,
            EquipmentOrderItemMapper equipmentOrderItemMapper,
            SysUserMapper sysUserMapper,
            EquipmentMapper equipmentMapper,
            EquipmentCartService equipmentCartService,
            WalletService walletService
    ) {
        this.equipmentOrderMapper = equipmentOrderMapper;
        this.equipmentOrderItemMapper = equipmentOrderItemMapper;
        this.sysUserMapper = sysUserMapper;
        this.equipmentMapper = equipmentMapper;
        this.equipmentCartService = equipmentCartService;
        this.walletService = walletService;
    }

    @Transactional
    public OrderDetailResponse createOrder(AuthenticatedUser principal, OrderCreateRequest request) {
        // 下单主流程（电商式“商品订单”）：
        //
        // 这一段代码是 equipment 模块的“一致性核心”，它把多件事绑在同一个事务里完成：
        // - 校验收货信息（订单必需字段）
        // - 读取购物车行（作为“用户想买什么”的来源）
        // - 读取商品行并计算应付总额 total（服务端重新计算，避免前端篡改金额）
        // - 对每个商品执行库存扣减（EquipmentMapper.subtractStock：CAS 条件更新，防超卖）
        // - 创建订单主表 equipment_order（先 CREATED）
        // - 创建订单明细 equipment_order_item（快照：商品名/单价/数量/小计）
        // - 钱包扣款（WalletService.debit：记录流水并扣余额）
        // - 更新订单为 PAID + paidAt
        // - 清空购物车
        //
        // 为什么要 @Transactional？
        // - 库存扣减、订单落库、钱包扣款必须“同生共死”。
        // - 任意一步异常都应回滚，避免出现：
        //   - 库存扣了但订单没生成 / 钱包没扣
        //   - 钱包扣了但订单未支付 / 库存未扣
        //   - 订单显示已支付但购物车未清空等
        //
        // 这里的“并发策略”不是显式加锁，而是依赖：
        // - subtractStock 的条件更新原子性（数据库层面防超卖）
        // - 事务回滚语义（发生冲突/库存不足时整体失败）
        Long userId = requireUserId(principal);
        if (request == null
                || !StringUtils.hasText(request.getReceiverName())
                || !StringUtils.hasText(request.getReceiverPhone())
                || !StringUtils.hasText(request.getReceiverAddress())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverName/receiverPhone/receiverAddress required");
        }

        List<EquipmentCartItem> cartRows = equipmentCartService.listCartRows(userId);
        if (cartRows == null || cartRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        Set<Long> equipmentIds = cartRows.stream().map(EquipmentCartItem::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (equipmentIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        List<Equipment> equipmentRows = equipmentMapper.selectByIds(equipmentIds);
        Map<Long, Equipment> equipmentMap = new HashMap<>();
        for (Equipment e : equipmentRows) {
            equipmentMap.put(e.getId(), e);
        }

        int total = 0;
        for (EquipmentCartItem ci : cartRows) {
            if (ci.getEquipmentId() == null) {
                continue;
            }
            Equipment e = equipmentMap.get(ci.getEquipmentId());
            if (e == null || !Objects.equals(e.getStatus(), "ON_SALE")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
            }
            int qty = ci.getQuantity() == null ? 0 : ci.getQuantity().intValue();
            if (qty <= 0) {
                continue;
            }
            int price = e.getPrice() == null ? 0 : e.getPrice().intValue();
            total += price * qty;
        }
        if (total <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        for (EquipmentCartItem ci : cartRows) {
            if (ci.getEquipmentId() == null) {
                continue;
            }
            int qty = ci.getQuantity() == null ? 0 : ci.getQuantity().intValue();
            if (qty <= 0) {
                continue;
            }
            int updated = equipmentMapper.subtractStock(ci.getEquipmentId(), qty);
            if (updated <= 0) {
                // subtractStock 返回 0 通常意味着：
                // - 库存不足（stock < qty）
                // - 或商品不在 ON_SALE（下架/禁售）
                //
                // 这里直接抛 409，触发事务回滚：
                // - 前面已扣成功的库存会被回滚（回到原值）
                // - 订单/明细/钱包扣款也不会落地
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
            }
        }

        LocalDateTime now = LocalDateTime.now();

        EquipmentOrder order = new EquipmentOrder();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setStatus("CREATED");
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setCreatedAt(now);
        equipmentOrderMapper.insert(order);

        for (EquipmentCartItem ci : cartRows) {
            if (ci.getEquipmentId() == null) {
                continue;
            }
            int qty = ci.getQuantity() == null ? 0 : ci.getQuantity().intValue();
            if (qty <= 0) {
                continue;
            }
            Equipment e = equipmentMap.get(ci.getEquipmentId());
            if (e == null) {
                continue;
            }
            int price = e.getPrice() == null ? 0 : e.getPrice().intValue();
            int subtotal = price * qty;

            EquipmentOrderItem item = new EquipmentOrderItem();
            item.setOrderId(order.getId());
            item.setEquipmentId(e.getId());
            item.setEquipmentName(e.getName());
            item.setPrice(price);
            item.setQuantity(qty);
            item.setSubtotal(subtotal);
            equipmentOrderItemMapper.insert(item);
        }

        if (total > 0) {
            // 钱包扣款：
            // - 这里的 total 是服务端基于商品行重新计算得到的金额
            // - debit 一般会做“余额检查 + 原子扣减 + 写流水”，并在余额不足时抛异常
            // - 由于在同一事务中，扣款失败会回滚库存扣减与订单落库
            walletService.debit(userId, total, "EQUIPMENT_ORDER", "equipment order", "EQUIPMENT_ORDER", order.getId());
        }

        // 订单状态机（简化版）：
        // CREATED（已创建待支付） -> PAID（已支付待发货） -> SHIPPED（已发货待收货） -> RECEIVED（已收货）
        //
        // 这里没有引入“待支付超时取消”的后台任务，因此 createOrder 流程会直接完成扣款并置为 PAID。
        order.setStatus("PAID");
        order.setPaidAt(now);
        equipmentOrderMapper.updateById(order);

        equipmentCartService.clearCart(userId);

        return getOrderDetail(principal, order.getId());
    }

    public OrderPageResponse myOrders(AuthenticatedUser principal, Integer page, Integer size, String status) {
        // 用户侧“我的订单”分页查询：
        // - 只按 userId 过滤，避免越权读取他人订单
        // - status 允许作为筛选条件（前端常用于 tab 过滤）
        // - 仍采用 count + limit/offset 的经典分页方式
        Long userId = requireUserId(principal);

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

        LambdaQueryWrapper<EquipmentOrder> countQw = buildOrderQuery(userId, status);
        long total = equipmentOrderMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<OrderListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<EquipmentOrder> listQw = buildOrderQuery(userId, status)
                .orderByDesc(EquipmentOrder::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<EquipmentOrder> rows = equipmentOrderMapper.selectList(listQw);
            for (EquipmentOrder row : rows) {
                OrderListItem item = new OrderListItem();
                item.setId(row.getId());
                item.setOrderNo(row.getOrderNo());
                item.setTotalAmount(row.getTotalAmount());
                item.setStatus(row.getStatus());
                item.setPaidAt(row.getPaidAt());
                item.setCreatedAt(row.getCreatedAt());
                items.add(item);
            }
        }

        OrderPageResponse resp = new OrderPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public OrderDetailResponse getOrderDetail(AuthenticatedUser principal, Long id) {
        // 用户侧“订单详情”：
        // - 先查 order
        // - 再做 userId 所属校验（防止水平越权）
        // - 最后组装 detail DTO（包含明细 items）
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        EquipmentOrder order = equipmentOrderMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        return toDetail(order);
    }

    @Transactional
    public OrderDetailResponse receive(AuthenticatedUser principal, Long id) {
        // 用户侧“确认收货”：
        // - 只有订单状态为 SHIPPED 才允许变更为 RECEIVED
        // - 这里用 updateById 直接写回，没有做 CAS 条件更新
        //   - 因为该系统里“确认收货”通常不会发生高并发写冲突
        //   - 并且仍有 status 校验兜底
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        EquipmentOrder order = equipmentOrderMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!Objects.equals(order.getStatus(), "SHIPPED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order not shipped");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("RECEIVED");
        order.setReceivedAt(now);
        equipmentOrderMapper.updateById(order);
        return getOrderDetail(principal, id);
    }

    public AdminOrderPageResponse adminOrders(Integer page, Integer size, String status, String orderNo, Long userId) {
        // 管理端/员工端“订单列表”（同一个 service 方法复用）：
        // - 是否有权限由 Controller 层 requireAdmin/requireStaff 兜底
        // - 这里纯做查询分页与过滤条件拼装
        // - 支持按 status / orderNo 模糊 / userId 精确过滤
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

        LambdaQueryWrapper<EquipmentOrder> countQw = buildAdminOrderQuery(status, orderNo, userId);
        long total = equipmentOrderMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<AdminOrderListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<EquipmentOrder> listQw = buildAdminOrderQuery(status, orderNo, userId)
                .orderByDesc(EquipmentOrder::getId)
                .last("LIMIT " + s + " OFFSET " + offset);
            List<EquipmentOrder> rows = equipmentOrderMapper.selectList(listQw);
            for (EquipmentOrder row : rows) {
                if (row == null) {
                    continue;
                }
                AdminOrderListItem it = new AdminOrderListItem();
                it.setId(row.getId());
                it.setOrderNo(row.getOrderNo());
                it.setUserId(row.getUserId());
                it.setTotalAmount(row.getTotalAmount());
                it.setStatus(row.getStatus());
                it.setReceiverName(row.getReceiverName());
                it.setReceiverPhone(row.getReceiverPhone());
                it.setReceiverAddress(row.getReceiverAddress());
                it.setLogisticsCompany(row.getLogisticsCompany());
                it.setTrackingNo(row.getTrackingNo());
                it.setPaidAt(row.getPaidAt());
                it.setShippedAt(row.getShippedAt());
                it.setReceivedAt(row.getReceivedAt());
                it.setCreatedAt(row.getCreatedAt());
                items.add(it);
            }
        }

        AdminOrderPageResponse resp = new AdminOrderPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public OrderDetailResponse adminOrderDetail(Long id) {
        // 管理端/员工端“订单详情”：
        // - 不做 userId 校验（因为平台角色可以查看全量订单）
        // - 权限同样由 Controller 层控制
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        EquipmentOrder order = equipmentOrderMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return toDetail(order);
    }

    @Transactional
    public OrderDetailResponse ship(AuthenticatedUser principal, Long id, OrderShipRequest request) {
        // 管理端/员工端“发货/补充物流信息”：
        // - 允许 PAID -> SHIPPED 的状态流转
        // - 同时也允许对已 SHIPPED 的订单“更新物流公司/单号”（例如录错后更正）
        // - shippedAt 只在第一次发货时写入（如果已存在就不覆盖，保留首发时间）
        //
        // 这里用了 @Transactional：
        // - 虽然只是更新订单一张表，但事务能保证该操作在未来扩展（例如发站内信/写操作日志）时仍具备一致性边界
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getLogisticsCompany()) || !StringUtils.hasText(request.getTrackingNo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "logisticsCompany/trackingNo required");
        }

        EquipmentOrder order = equipmentOrderMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        String st = order.getStatus();
        if (!Objects.equals(st, "PAID") && !Objects.equals(st, "SHIPPED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order status invalid");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setLogisticsCompany(request.getLogisticsCompany().trim());
        order.setTrackingNo(request.getTrackingNo().trim());
        if (!Objects.equals(order.getStatus(), "SHIPPED")) {
            order.setStatus("SHIPPED");
        }
        if (order.getShippedAt() == null) {
            order.setShippedAt(now);
        }
        equipmentOrderMapper.updateById(order);
        return adminOrderDetail(id);
    }

    private LambdaQueryWrapper<EquipmentOrder> buildOrderQuery(Long userId, String status) {
        // 用户侧查询条件构造：强制带 userId（这是最重要的安全边界）
        LambdaQueryWrapper<EquipmentOrder> qw = new LambdaQueryWrapper<EquipmentOrder>()
            .eq(EquipmentOrder::getUserId, userId);
        if (StringUtils.hasText(status)) {
            qw.eq(EquipmentOrder::getStatus, status);
        }
        return qw;
    }

    private LambdaQueryWrapper<EquipmentOrder> buildAdminOrderQuery(String status, String orderNo, Long userId) {
        // 管理端查询条件构造：可选条件组合
        LambdaQueryWrapper<EquipmentOrder> qw = new LambdaQueryWrapper<EquipmentOrder>();
        if (StringUtils.hasText(status)) {
            qw.eq(EquipmentOrder::getStatus, status.trim());
        }
        if (StringUtils.hasText(orderNo)) {
            qw.like(EquipmentOrder::getOrderNo, orderNo.trim());
        }
        if (userId != null) {
            qw.eq(EquipmentOrder::getUserId, userId);
        }
        return qw;
    }

    private OrderDetailResponse toDetail(EquipmentOrder order) {
        // 组装订单详情 DTO：
        // - 读取下单用户（用于展示 username）
        // - 读取订单明细（items）
        //
        // 注：这里按 orderId 查询明细是 1:N 关系的典型做法。
        // 对于“订单详情”场景，这是可接受的（单订单一条主表 + 若干明细）。
        SysUser user = order == null || order.getUserId() == null ? null : sysUserMapper.selectById(order.getUserId());

        List<EquipmentOrderItem> itemRows = equipmentOrderItemMapper.selectList(
            new LambdaQueryWrapper<EquipmentOrderItem>()
                .eq(EquipmentOrderItem::getOrderId, order.getId())
                .orderByAsc(EquipmentOrderItem::getId)
        );

        List<OrderItemResponse> items = new ArrayList<>();
        for (EquipmentOrderItem r : itemRows) {
            OrderItemResponse item = new OrderItemResponse();
            item.setEquipmentId(r.getEquipmentId());
            item.setEquipmentName(r.getEquipmentName());
            item.setPrice(r.getPrice());
            item.setQuantity(r.getQuantity());
            item.setSubtotal(r.getSubtotal());
            items.add(item);
        }

        OrderDetailResponse resp = new OrderDetailResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setUserId(order.getUserId());
        resp.setUsername(user == null ? null : user.getUsername());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setStatus(order.getStatus());
        resp.setReceiverName(order.getReceiverName());
        resp.setReceiverPhone(order.getReceiverPhone());
        resp.setReceiverAddress(order.getReceiverAddress());
        resp.setLogisticsCompany(order.getLogisticsCompany());
        resp.setTrackingNo(order.getTrackingNo());
        resp.setPaidAt(order.getPaidAt());
        resp.setShippedAt(order.getShippedAt());
        resp.setReceivedAt(order.getReceivedAt());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setItems(items);
        return resp;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
