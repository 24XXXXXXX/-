package com.communitysport.home.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.communitysport.banner.dto.HomeBannerItem;
import com.communitysport.equipment.dto.EquipmentListItem;
import com.communitysport.notice.dto.NoticeListItem;
import com.communitysport.venue.dto.VenueListItem;

public class HomeRecommendationsResponse {

    private LocalDateTime generatedAt;

    private List<HomeBannerItem> banners;

    private List<VenueListItem> hotVenues;

    private List<HomeCourseItem> qualityCourses;

    private List<EquipmentListItem> equipmentDeals;

    private List<NoticeListItem> notices;

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<HomeBannerItem> getBanners() {
        return banners;
    }

    public void setBanners(List<HomeBannerItem> banners) {
        this.banners = banners;
    }

    public List<VenueListItem> getHotVenues() {
        return hotVenues;
    }

    public void setHotVenues(List<VenueListItem> hotVenues) {
        this.hotVenues = hotVenues;
    }

    public List<HomeCourseItem> getQualityCourses() {
        return qualityCourses;
    }

    public void setQualityCourses(List<HomeCourseItem> qualityCourses) {
        this.qualityCourses = qualityCourses;
    }

    public List<EquipmentListItem> getEquipmentDeals() {
        return equipmentDeals;
    }

    public void setEquipmentDeals(List<EquipmentListItem> equipmentDeals) {
        this.equipmentDeals = equipmentDeals;
    }

    public List<NoticeListItem> getNotices() {
        return notices;
    }

    public void setNotices(List<NoticeListItem> notices) {
        this.notices = notices;
    }
}
