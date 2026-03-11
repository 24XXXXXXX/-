package com.communitysport.booking.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.booking.entity.VenueVerificationLog;

/**
 * 场地核销日志 Mapper。
 *
 * <p>核销日志属于审计数据：只增不改（业务上通常不会删除/更新）。
 */
@Mapper
public interface VenueVerificationLogMapper extends BaseMapper<VenueVerificationLog> {
}
