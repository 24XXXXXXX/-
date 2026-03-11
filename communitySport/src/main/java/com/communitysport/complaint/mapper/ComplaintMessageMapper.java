package com.communitysport.complaint.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.complaint.entity.ComplaintMessage;

@Mapper
public interface ComplaintMessageMapper extends BaseMapper<ComplaintMessage> {

    // ComplaintMessage Mapper：
    // - 工单沟通消息表的访问接口
    // - 常见查询模式：按 complaint_id 查询并按 id/created_at 排序，形成“对话流”
}
