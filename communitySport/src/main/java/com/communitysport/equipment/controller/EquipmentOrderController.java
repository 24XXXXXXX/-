package com.communitysport.equipment.controller;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.equipment.dto.AdminOrderPageResponse;
import com.communitysport.equipment.dto.OrderCreateRequest;
import com.communitysport.equipment.dto.OrderDetailResponse;
import com.communitysport.equipment.dto.OrderShipRequest;
import com.communitysport.equipment.dto.OrderPageResponse;
import com.communitysport.equipment.service.EquipmentOrderService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class EquipmentOrderController {

    // 器材商城-订单 Controller：
    // - 用户端：下单、我的订单列表/详情、确认收货
    // - 管理端（ROLE_ADMIN）：全量订单列表/详情、发货
    // - 员工端（ROLE_STAFF 或 ROLE_ADMIN）：同样提供后台列表/详情、发货（用于日常履约）
    //
    // 说明：
    // - 管理/员工接口以 /api/admin 或 /api/staff 开头，除 SecurityConfig 的路由鉴权外，
    //   Controller 内再次 requireAdmin/requireStaff 属于“防御式校验”，让权限边界在入口更直观。
    // - 发货（ship）属于履约关键动作：需要记录操作人（au）以便审计。

    private final EquipmentOrderService equipmentOrderService;

    public EquipmentOrderController(EquipmentOrderService equipmentOrderService) {
        this.equipmentOrderService = equipmentOrderService;
    }

    @PostMapping("/api/equipment/orders")
    public OrderDetailResponse create(Authentication authentication, @RequestBody OrderCreateRequest request) {
        // 用户下单：通常会在 Service 内完成库存扣减、钱包扣款/流水等（属于强一致事务）。
        return equipmentOrderService.createOrder(getPrincipal(authentication), request);
    }

    @GetMapping("/api/equipment/orders")
    public OrderPageResponse myOrders(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    ) {
        // 用户端：我的器材订单列表（分页 + 可选状态筛选）。
        return equipmentOrderService.myOrders(getPrincipal(authentication), page, size, status);
    }

    @GetMapping("/api/equipment/orders/{id}")
    public OrderDetailResponse detail(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：订单详情（Service 会校验订单归属，避免越权查看）。
        return equipmentOrderService.getOrderDetail(getPrincipal(authentication), id);
    }

    @PostMapping("/api/equipment/orders/{id}/receive")
    public OrderDetailResponse receive(Authentication authentication, @PathVariable("id") Long id) {
        // 用户端：确认收货。
        // - 一般会要求订单状态处于“已发货”，并转为“已完成”（状态机由 Service 控制）。
        return equipmentOrderService.receive(getPrincipal(authentication), id);
    }

    @GetMapping("/api/admin/equipment/orders")
    public AdminOrderPageResponse adminList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "orderNo", required = false) String orderNo,
            @RequestParam(name = "userId", required = false) Long userId
    ) {
        // 管理端：全量订单列表（支持按 status/orderNo/userId 过滤）。
        requireAdmin(authentication);
        return equipmentOrderService.adminOrders(page, size, status, orderNo, userId);
    }

    @GetMapping("/api/admin/equipment/orders/{id}")
    public OrderDetailResponse adminDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 管理端：订单详情。
        requireAdmin(authentication);
        return equipmentOrderService.adminOrderDetail(id);
    }

    @PostMapping("/api/admin/equipment/orders/{id}/ship")
    public OrderDetailResponse adminShip(Authentication authentication, @PathVariable("id") Long id, @RequestBody OrderShipRequest request) {
        // 管理端：发货。
        // - 需要记录操作人（au），用于审计“是谁执行了发货”
        // - 具体发货字段（快递单号/承运商等）与状态流转由 Service 校验
        AuthenticatedUser au = getPrincipal(authentication);
        requireAdmin(authentication);
        return equipmentOrderService.ship(au, id, request);
    }

    @GetMapping("/api/staff/equipment/orders")
    public AdminOrderPageResponse staffList(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "orderNo", required = false) String orderNo,
            @RequestParam(name = "userId", required = false) Long userId
    ) {
        // 员工端：后台订单列表（权限：ROLE_STAFF 或 ROLE_ADMIN）。
        requireStaff(authentication);
        return equipmentOrderService.adminOrders(page, size, status, orderNo, userId);
    }

    @GetMapping("/api/staff/equipment/orders/{id}")
    public OrderDetailResponse staffDetail(Authentication authentication, @PathVariable("id") Long id) {
        // 员工端：后台订单详情。
        requireStaff(authentication);
        return equipmentOrderService.adminOrderDetail(id);
    }

    @PostMapping("/api/staff/equipment/orders/{id}/ship")
    public OrderDetailResponse staffShip(Authentication authentication, @PathVariable("id") Long id, @RequestBody OrderShipRequest request) {
        // 员工端：发货（与管理端同逻辑，差别在权限）。
        // - 同样需要记录操作人（au）用于审计
        AuthenticatedUser au = getPrincipal(authentication);
        requireStaff(authentication);
        return equipmentOrderService.ship(au, id, request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 统一 principal 提取：
        // - authentication/principal 为空或类型不匹配 => 401
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        return au;
    }

    private void requireAdmin(Authentication authentication) {
        // 角色校验：必须拥有 ROLE_ADMIN。
        // - 401：未登录
        // - 403：已登录但无管理员权限
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean ok = false;
        if (authorities != null) {
            for (GrantedAuthority a : authorities) {
                if (a == null) {
                    continue;
                }
                String auth = a.getAuthority();
                if (StringUtils.hasText(auth) && "ROLE_ADMIN".equals(auth)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private void requireStaff(Authentication authentication) {
        // 角色校验：ROLE_STAFF 或 ROLE_ADMIN。
        // - 401：未登录
        // - 403：已登录但无员工/管理员权限
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean ok = false;
        if (authorities != null) {
            for (GrantedAuthority a : authorities) {
                if (a == null) {
                    continue;
                }
                String auth = a.getAuthority();
                if (StringUtils.hasText(auth) && ("ROLE_ADMIN".equals(auth) || "ROLE_STAFF".equals(auth))) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
