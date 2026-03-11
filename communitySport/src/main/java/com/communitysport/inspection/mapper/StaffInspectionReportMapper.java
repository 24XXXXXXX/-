package com.communitysport.inspection.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.inspection.entity.StaffInspectionReport;

@Mapper
public interface StaffInspectionReportMapper extends BaseMapper<StaffInspectionReport> {

    // 员工巡检上报主表 Mapper：
    // - BaseMapper 提供 CRUD
    // - 本模块的筛选/分页查询主要在 Service 中通过 LambdaQueryWrapper 构造
}
