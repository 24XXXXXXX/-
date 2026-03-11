package com.communitysport.signin.dto;

public class SigninResponse {

    private boolean signed;

    private String signinDate;

    private int streak;

    private int dailyReward;

    private int streakBonus;

    private int totalReward;

    public boolean isSigned() {
        return signed;
    }

    public void setSigned(boolean signed) {
        this.signed = signed;
    }

    public String getSigninDate() {
        return signinDate;
    }

    public void setSigninDate(String signinDate) {
        this.signinDate = signinDate;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public int getDailyReward() {
        return dailyReward;
    }

    public void setDailyReward(int dailyReward) {
        this.dailyReward = dailyReward;
    }

    public int getStreakBonus() {
        return streakBonus;
    }

    public void setStreakBonus(int streakBonus) {
        this.streakBonus = streakBonus;
    }

    public int getTotalReward() {
        return totalReward;
    }

    public void setTotalReward(int totalReward) {
        this.totalReward = totalReward;
    }
}
