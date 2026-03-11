package com.communitysport.notice.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.notice.entity.Notice;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
}
