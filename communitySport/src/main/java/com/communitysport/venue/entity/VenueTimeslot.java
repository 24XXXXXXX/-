package com.communitysport.venue.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 场地可预约时段实体（venue_timeslot）。
 *
 * <p>它代表“某个场地在某个具体时间段可以被预约”。
 * 在场地业务里，Timeslot 与 Booking 的关系非常紧密：
 * <p>- Booking 下单前必须先把 Timeslot 从 AVAILABLE 原子占用为 BOOKED
 * <p>- 取消/退款后会尝试把 BOOKED 释放回 AVAILABLE
 *
 * <p>关键字段：
 * <p>- startTime/endTime：确定预约起止时间
 * <p>- price：该时段价格（通常是最小单位整数）
 * <p>- status：时段状态
 *   <p>  - AVAILABLE：可预约
 *   <p>  - BOOKED：已被某笔订单占用
 *   <p>  - BLOCKED：后台封禁/维护，不允许预约
 *
 * <p>为什么 status 设计成“行内字段”而不是从 booking 推导？
 * <p>- 因为并发控制需要一个可条件更新的字段（CAS），用 booking 推导会变得很复杂且易出错。
 */
@TableName("venue_timeslot")
public class VenueTimeslot {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("venue_id")
    private Long venueId;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    private Integer price;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
