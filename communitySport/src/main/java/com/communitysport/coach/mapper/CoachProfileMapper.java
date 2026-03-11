package com.communitysport.coach.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.coach.entity.CoachProfile;

@Mapper
public interface CoachProfileMapper extends BaseMapper<CoachProfile> {

    @Update("UPDATE coach_profile "
        + "SET rating_avg = ROUND((rating_avg * rating_count + #{rating}) / (rating_count + 1), 2), "
        + "rating_count = rating_count + 1 "
        + "WHERE user_id = #{coachUserId}")
    int addRating(@Param("coachUserId") Long coachUserId, @Param("rating") int rating);
}
