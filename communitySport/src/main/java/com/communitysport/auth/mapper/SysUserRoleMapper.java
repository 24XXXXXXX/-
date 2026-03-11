package com.communitysport.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.auth.entity.SysUserRole;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Long> selectUserIdsByRoleIdPage(@Param("roleId") Long roleId, @Param("offset") long offset, @Param("limit") int limit);

    @Select("<script>"
        + "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id WHERE ur.role_id = #{roleId}"
        + "<if test='status != null'> AND u.status = #{status}</if>"
        + "<if test='keyword != null and keyword != \"\"'>"
        + " AND (u.username LIKE CONCAT('%',#{keyword},'%') OR u.nickname LIKE CONCAT('%',#{keyword},'%')"
        + " OR u.phone LIKE CONCAT('%',#{keyword},'%') OR u.email LIKE CONCAT('%',#{keyword},'%'))"
        + "</if>"
        + "</script>")
    long countUserIdsByRoleIdFiltered(
            @Param("roleId") Long roleId,
            @Param("status") Integer status,
            @Param("keyword") String keyword
    );

    @Select("<script>"
        + "SELECT ur.user_id FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id WHERE ur.role_id = #{roleId}"
        + "<if test='status != null'> AND u.status = #{status}</if>"
        + "<if test='keyword != null and keyword != \"\"'>"
        + " AND (u.username LIKE CONCAT('%',#{keyword},'%') OR u.nickname LIKE CONCAT('%',#{keyword},'%')"
        + " OR u.phone LIKE CONCAT('%',#{keyword},'%') OR u.email LIKE CONCAT('%',#{keyword},'%'))"
        + "</if>"
        + " ORDER BY ur.id DESC LIMIT #{limit} OFFSET #{offset}"
        + "</script>")
    List<Long> selectUserIdsByRoleIdPageFiltered(
            @Param("roleId") Long roleId,
            @Param("status") Integer status,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit
    );
}
