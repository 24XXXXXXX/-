package com.communitysport.signin.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_signin_log")
public class UserSigninLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("signin_date")
    private LocalDate signinDate;

    @TableField("daily_reward")
    private Integer dailyReward;

    @TableField("streak_bonus")
    private Integer streakBonus;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getSigninDate() {
        return signinDate;
    }

    public void setSigninDate(LocalDate signinDate) {
        this.signinDate = signinDate;
    }

    public Integer getDailyReward() {
        return dailyReward;
    }

    public void setDailyReward(Integer dailyReward) {
        this.dailyReward = dailyReward;
    }

    public Integer getStreakBonus() {
        return streakBonus;
    }

    public void setStreakBonus(Integer streakBonus) {
        this.streakBonus = streakBonus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
