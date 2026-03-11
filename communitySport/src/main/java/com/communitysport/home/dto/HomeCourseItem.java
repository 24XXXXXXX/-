package com.communitysport.home.dto;

import java.math.BigDecimal;

import com.communitysport.course.dto.CourseListItem;

public class HomeCourseItem {

    private CourseListItem course;

    private BigDecimal coachRatingAvg;

    private Integer coachRatingCount;

    public CourseListItem getCourse() {
        return course;
    }

    public void setCourse(CourseListItem course) {
        this.course = course;
    }

    public BigDecimal getCoachRatingAvg() {
        return coachRatingAvg;
    }

    public void setCoachRatingAvg(BigDecimal coachRatingAvg) {
        this.coachRatingAvg = coachRatingAvg;
    }

    public Integer getCoachRatingCount() {
        return coachRatingCount;
    }

    public void setCoachRatingCount(Integer coachRatingCount) {
        this.coachRatingCount = coachRatingCount;
    }
}
