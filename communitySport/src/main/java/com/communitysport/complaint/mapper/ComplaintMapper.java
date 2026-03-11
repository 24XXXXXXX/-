package com.communitysport.complaint.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.complaint.entity.Complaint;

@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {

    // Complaint 主表 Mapper：
    // - BaseMapper 提供 CRUD
    // - 这里补充了两类统计口径：提交数/解决数
    //
    // 统计口径约定：
    // - 时间范围采用半开区间 [start, end) 避免边界重复统计
    // - 解决数以 resolved_at 非空为准（并以 resolved_at 落在区间内作为统计口径）

    @Select("SELECT COUNT(1) FROM complaint WHERE created_at >= #{start} AND created_at < #{end}")
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(1) FROM complaint WHERE resolved_at IS NOT NULL AND resolved_at >= #{start} AND resolved_at < #{end}")
    long countResolvedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
