package com.communitysport.message.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.message.entity.UserMessage;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
