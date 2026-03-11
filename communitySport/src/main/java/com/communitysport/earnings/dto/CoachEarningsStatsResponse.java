package com.communitysport.earnings.dto;

public class CoachEarningsStatsResponse {

    private Long totalEarnings;

    private Long totalExpense;

    private Integer availableBalance;

    private Long withdrawnAmount;

    public Long getTotalEarnings() {
        return totalEarnings;
    }

    public void setTotalEarnings(Long totalEarnings) {
        this.totalEarnings = totalEarnings;
    }

    public Long getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(Long totalExpense) {
        this.totalExpense = totalExpense;
    }

    public Integer getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(Integer availableBalance) {
        this.availableBalance = availableBalance;
    }

    public Long getWithdrawnAmount() {
        return withdrawnAmount;
    }

    public void setWithdrawnAmount(Long withdrawnAmount) {
        this.withdrawnAmount = withdrawnAmount;
    }
}
