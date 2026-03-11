package com.communitysport.signin.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.signin.entity.UserSigninLog;

@Mapper
public interface UserSigninLogMapper extends BaseMapper<UserSigninLog> {
}
