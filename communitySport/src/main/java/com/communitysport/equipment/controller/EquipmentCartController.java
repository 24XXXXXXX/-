package com.communitysport.equipment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.equipment.dto.CartResponse;
import com.communitysport.equipment.dto.CartUpdateRequest;
import com.communitysport.equipment.service.EquipmentCartService;
import com.communitysport.security.AuthenticatedUser;

@RestController
public class EquipmentCartController {

    private final EquipmentCartService equipmentCartService;

    public EquipmentCartController(EquipmentCartService equipmentCartService) {
        this.equipmentCartService = equipmentCartService;
    }

    @GetMapping("/api/equipment/cart")
    public CartResponse cart(Authentication authentication) {
        // 用户侧：查看购物车。
        //
        // 这里 Controller 的职责是：
        // - 从 Spring Security 的 Authentication 中提取业务 principal（AuthenticatedUser）
        // - 把业务请求转交给 Service（Service 负责数据校验、读模型组装、异常转换）
        return equipmentCartService.getCart(getPrincipal(authentication));
    }

    @PostMapping("/api/equipment/cart")
    public CartResponse update(Authentication authentication, @RequestBody CartUpdateRequest request) {
        // 用户侧：更新购物车。
        //
        // 这个接口语义是“覆盖式更新”（更接近 PUT）：
        // - request.quantity <= 0 表示删除
        // - request.quantity > 0 表示设置数量
        //
        // 具体的幂等与并发补偿逻辑在 Service 层实现（例如 DuplicateKeyException 的处理）。
        return equipmentCartService.updateCart(getPrincipal(authentication), request);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 认证边界：
        // - 只要 authentication 或 principal 不存在，就视为未登录
        // - 只有 principal 类型为 AuthenticatedUser 才认为是本系统的已登录用户
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser au)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        return au;
    }
}
