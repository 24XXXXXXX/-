package com.communitysport.signin.dto;

public class SigninStatusResponse {

    private String today;

    private boolean todaySigned;

    private int streak;

    private String lastSigninDate;

    public String getToday() {
        return today;
    }

    public void setToday(String today) {
        this.today = today;
    }

    public boolean isTodaySigned() {
        return todaySigned;
    }

    public void setTodaySigned(boolean todaySigned) {
        this.todaySigned = todaySigned;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public String getLastSigninDate() {
        return lastSigninDate;
    }

    public void setLastSigninDate(String lastSigninDate) {
        this.lastSigninDate = lastSigninDate;
    }
}
