package com.communitysport.video.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.video.entity.CoachVideo;

@Mapper
public interface CoachVideoMapper extends BaseMapper<CoachVideo> {

    // CoachVideo 主表 Mapper：
    // - 基于 MyBatis-Plus BaseMapper 提供常用 CRUD
    // - 复杂的筛选/分页通常在 Service 中使用 LambdaQueryWrapper 构造条件
}
