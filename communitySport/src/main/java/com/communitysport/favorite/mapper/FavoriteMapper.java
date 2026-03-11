package com.communitysport.favorite.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.favorite.entity.Favorite;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
