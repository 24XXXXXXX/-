package com.communitysport.auth.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.auth.entity.SysUser;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT COUNT(1) FROM sys_user WHERE created_at >= #{start} AND created_at < #{end}")
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(1) FROM sys_user WHERE last_login_at IS NOT NULL AND last_login_at >= #{start} AND last_login_at < #{end}")
    long countLastLoginBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
