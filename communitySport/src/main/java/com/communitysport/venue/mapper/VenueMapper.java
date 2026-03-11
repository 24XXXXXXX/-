package com.communitysport.venue.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.venue.entity.Venue;

/**
 * 场地（venue）Mapper。
 *
 * <p>除 BaseMapper 默认 CRUD 外，这里额外提供 increaseClickCount：
 * <p>- 用于公开详情页的点击量自增
 * <p>- 使用 COALESCE(click_count, 0) 防止空值导致 +1 异常
 * <p>- 该计数是“尽力而为”的运营指标，不要求强一致
 */
@Mapper
public interface VenueMapper extends BaseMapper<Venue> {

    @Update("UPDATE venue SET click_count = COALESCE(click_count, 0) + 1 WHERE id = #{id}")
    int increaseClickCount(@Param("id") Long id);
}
