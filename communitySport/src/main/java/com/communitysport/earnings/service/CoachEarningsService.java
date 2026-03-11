package com.communitysport.earnings.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.earnings.dto.CoachEarningItem;
import com.communitysport.earnings.dto.CoachEarningPageResponse;
import com.communitysport.earnings.dto.CoachEarningsStatsResponse;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.wallet.dto.WalletBalanceResponse;
import com.communitysport.wallet.entity.WalletTransaction;
import com.communitysport.wallet.mapper.WalletTransactionMapper;
import com.communitysport.wallet.service.WalletService;

@Service
public class CoachEarningsService {

    private final WalletService walletService;

    private final WalletTransactionMapper walletTransactionMapper;

    public CoachEarningsService(WalletService walletService, WalletTransactionMapper walletTransactionMapper) {
        this.walletService = walletService;
        this.walletTransactionMapper = walletTransactionMapper;
    }

    @Transactional
    public CoachEarningsStatsResponse stats(AuthenticatedUser principal) {
        Long coachUserId = requireUserId(principal);

        WalletBalanceResponse bal = walletService.getBalance(principal);
        int available = bal == null || bal.getBalance() == null ? 0 : bal.getBalance().intValue();

        long sumIn = walletTransactionMapper.sumInAmountByUserId(coachUserId);
        long withdrawn = walletTransactionMapper.sumWithdrawOutAmount(coachUserId);
        long totalExpense = walletTransactionMapper.sumOutAmountByUserId(coachUserId);
        long derived = (long) available + withdrawn;
        long totalEarnings = Math.max(sumIn, derived);

        CoachEarningsStatsResponse resp = new CoachEarningsStatsResponse();
        resp.setTotalEarnings(totalEarnings);
        resp.setTotalExpense(totalExpense);
        resp.setAvailableBalance(available);
        resp.setWithdrawnAmount(withdrawn);
        return resp;
    }

    public CoachEarningPageResponse list(AuthenticatedUser principal, Integer page, Integer size) {
        Long coachUserId = requireUserId(principal);

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

        long total = walletTransactionMapper.countByUserId(coachUserId);
        long offset = (long) (p - 1) * s;

        List<CoachEarningItem> items = new ArrayList<>();
        if (offset < total) {
            List<WalletTransaction> rows = walletTransactionMapper.selectPageByUserId(coachUserId, offset, s);
            if (rows != null) {
                for (WalletTransaction r : rows) {
                    if (r == null) {
                        continue;
                    }
                    CoachEarningItem it = new CoachEarningItem();
                    it.setId(r.getId());
                    it.setType(mapType(r.getTxnType()));
                    it.setDescription(r.getRemark());
                    Integer amount = r.getAmount();
                    int signed = amount == null ? 0 : amount.intValue();
                    if ("OUT".equals(r.getDirection())) {
                        signed = -signed;
                    }
                    it.setAmount(signed);
                    it.setCreatedAt(r.getCreatedAt());
                    items.add(it);
                }
            }
        }

        CoachEarningPageResponse resp = new CoachEarningPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private String mapType(String txnType) {
        if (txnType == null) {
            return null;
        }
        if ("COACH_COURSE_EARNING".equals(txnType)) {
            return "course";
        }
        if ("COACH_VIDEO_EARNING".equals(txnType)) {
            return "video";
        }
        if ("WITHDRAW".equals(txnType)) {
            return "withdraw";
        }
        if ("COURSE_BOOKING".equals(txnType)) {
            return "course_booking";
        }
        if ("COACH_VIDEO".equals(txnType)) {
            return "video_purchase";
        }
        if ("TOPUP".equals(txnType)) {
            return "topup";
        }
        if ("SIGNIN".equals(txnType)) {
            return "signin";
        }
        return txnType;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
