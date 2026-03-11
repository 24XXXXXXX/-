package com.communitysport.wallet.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.security.AuthenticatedUser;
import com.communitysport.wallet.dto.WalletBalanceResponse;
import com.communitysport.wallet.dto.WalletTransactionPageResponse;
import com.communitysport.wallet.service.WalletService;

/**
 * 钱包接口（用户端）。
 *
 * <p>本 Controller 提供用户在“我的钱包”页面最常用的两个能力：
 * <p>- 查询余额：/api/wallet/balance
 * <p>- 查询资金流水：/api/wallet/transactions
 *
 * <p>注意：
 * <p>- 这里不会直接做扣款/加款，资金变动由各业务模块调用 WalletService.credit/debit 完成。
 * <p>- Controller 只负责从 Authentication 中取出当前登录用户，并转交 Service。
 */
@RestController
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/api/wallet/balance")
    public WalletBalanceResponse balance(Authentication authentication) {
        // 查询余额：要求登录。
        return walletService.getBalance(getPrincipal(authentication));
    }

    @GetMapping("/api/wallet/transactions")
    public WalletTransactionPageResponse transactions(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        // 查询资金流水（分页）：要求登录。
        return walletService.getTransactions(getPrincipal(authentication), page, size);
    }

    private AuthenticatedUser getPrincipal(Authentication authentication) {
        // 从 Spring Security 上下文中取出当前登录用户。
        // JwtAuthenticationFilter 会把解析出的用户信息写入 authentication.principal。
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
