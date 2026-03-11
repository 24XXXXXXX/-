package com.communitysport.home.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.banner.dto.HomeBannerItem;
import com.communitysport.banner.service.HomeBannerService;
import com.communitysport.coach.entity.CoachProfile;
import com.communitysport.coach.mapper.CoachProfileMapper;
import com.communitysport.course.dto.CourseListItem;
import com.communitysport.course.dto.CoursePageResponse;
import com.communitysport.course.service.CourseService;
import com.communitysport.equipment.dto.EquipmentListItem;
import com.communitysport.equipment.dto.EquipmentPageResponse;
import com.communitysport.equipment.service.EquipmentCatalogService;
import com.communitysport.favorite.entity.Favorite;
import com.communitysport.favorite.mapper.FavoriteMapper;
import com.communitysport.home.dto.HomeCourseItem;
import com.communitysport.home.dto.HomeRecommendationsResponse;
import com.communitysport.notice.dto.NoticeListItem;
import com.communitysport.notice.dto.NoticePageResponse;
import com.communitysport.notice.service.NoticeService;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.venue.dto.VenueListItem;
import com.communitysport.venue.dto.VenuePageResponse;
import com.communitysport.venue.service.VenueService;

@Service
public class HomeRecommendationService {

    private final HomeBannerService homeBannerService;

    private final VenueService venueService;

    private final CourseService courseService;

    private final CoachProfileMapper coachProfileMapper;

    private final EquipmentCatalogService equipmentCatalogService;

    private final NoticeService noticeService;

    private final FavoriteMapper favoriteMapper;

    public HomeRecommendationService(
            HomeBannerService homeBannerService,
            VenueService venueService,
            CourseService courseService,
            CoachProfileMapper coachProfileMapper,
            EquipmentCatalogService equipmentCatalogService,
            NoticeService noticeService,
            FavoriteMapper favoriteMapper
    ) {
        this.homeBannerService = homeBannerService;
        this.venueService = venueService;
        this.courseService = courseService;
        this.coachProfileMapper = coachProfileMapper;
        this.equipmentCatalogService = equipmentCatalogService;
        this.noticeService = noticeService;
        this.favoriteMapper = favoriteMapper;
    }

    public HomeRecommendationsResponse recommendations(
            AuthenticatedUser principal,
            Integer bannerSize,
            Integer venueSize,
            Integer courseSize,
            Integer equipmentSize,
            Integer noticeSize
    ) {
        int bs = normalizeSize(bannerSize, 10);
        int vs = normalizeSize(venueSize, 6);
        int cs = normalizeSize(courseSize, 6);
        int es = normalizeSize(equipmentSize, 6);
        int ns = normalizeSize(noticeSize, 5);

        List<HomeBannerItem> banners = homeBannerService.listPublic();
        if (banners != null && banners.size() > bs) {
            banners = new ArrayList<>(banners.subList(0, bs));
        }

        List<VenueListItem> hotVenues = pickHotVenues(vs);

        List<HomeCourseItem> qualityCourses = pickQualityCourses(cs);

        List<EquipmentListItem> deals = pickEquipmentDeals(es);

        if (principal != null && principal.userId() != null) {
            hotVenues = personalizeVenues(principal.userId(), hotVenues);
            qualityCourses = personalizeCourses(principal.userId(), qualityCourses);
            deals = personalizeEquipments(principal.userId(), deals);
        }

        NoticePageResponse noticePage = noticeService.listPublic(1, ns, null, null);
        List<NoticeListItem> notices = noticePage == null ? null : noticePage.getItems();

        HomeRecommendationsResponse resp = new HomeRecommendationsResponse();
        resp.setGeneratedAt(LocalDateTime.now());
        resp.setBanners(banners);
        resp.setHotVenues(hotVenues);
        resp.setQualityCourses(qualityCourses);
        resp.setEquipmentDeals(deals);
        resp.setNotices(notices);
        return resp;
    }

    private List<VenueListItem> pickHotVenues(int size) {
        VenuePageResponse page = venueService.listVenues(1, 100, null, null, "ACTIVE");
        List<VenueListItem> items = page == null ? null : page.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<VenueListItem> sorted = new ArrayList<>(items);
        sorted.sort(
            Comparator
                .comparing((VenueListItem v) -> v == null ? null : v.getClickCount(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(v -> v == null ? null : v.getId(), Comparator.nullsLast(Comparator.reverseOrder()))
        );

        if (sorted.size() > size) {
            return sorted.subList(0, size);
        }
        return sorted;
    }

    private List<VenueListItem> personalizeVenues(Long userId, List<VenueListItem> venues) {
        if (userId == null || venues == null || venues.isEmpty()) {
            return venues == null ? List.of() : venues;
        }

        Set<Long> ids = venues.stream().map(VenueListItem::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return venues;
        }

        Set<Long> favIds = loadFavoriteTargetIds(userId, "VENUE", ids);
        if (favIds.isEmpty()) {
            return venues;
        }

        List<VenueListItem> sorted = new ArrayList<>(venues);
        sorted.sort(
            Comparator
                .<VenueListItem>comparingInt(v -> v != null && v.getId() != null && favIds.contains(v.getId()) ? 1 : 0)
                .reversed()
                .thenComparing((VenueListItem v) -> v == null ? null : v.getClickCount(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(v -> v == null ? null : v.getId(), Comparator.nullsLast(Comparator.reverseOrder()))
        );
        return sorted;
    }

    private List<HomeCourseItem> personalizeCourses(Long userId, List<HomeCourseItem> courses) {
        if (userId == null || courses == null || courses.isEmpty()) {
            return courses == null ? List.of() : courses;
        }

        Set<Long> ids = courses.stream()
            .map(c -> c == null || c.getCourse() == null ? null : c.getCourse().getId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return courses;
        }

        Set<Long> favIds = loadFavoriteTargetIds(userId, "COURSE", ids);
        if (favIds.isEmpty()) {
            return courses;
        }

        List<HomeCourseItem> sorted = new ArrayList<>(courses);
        sorted.sort((a, b) -> {
            Long ida = a == null || a.getCourse() == null ? null : a.getCourse().getId();
            Long idb = b == null || b.getCourse() == null ? null : b.getCourse().getId();
            int fa = ida != null && favIds.contains(ida) ? 1 : 0;
            int fb = idb != null && favIds.contains(idb) ? 1 : 0;
            if (fa != fb) {
                return Integer.compare(fb, fa);
            }

            BigDecimal ra = a == null ? null : a.getCoachRatingAvg();
            BigDecimal rb = b == null ? null : b.getCoachRatingAvg();
            if (ra == null && rb != null) {
                return 1;
            }
            if (ra != null && rb == null) {
                return -1;
            }
            if (ra != null && rb != null) {
                int cmp = rb.compareTo(ra);
                if (cmp != 0) {
                    return cmp;
                }
            }

            Integer ca = a == null ? null : a.getCoachRatingCount();
            Integer cb = b == null ? null : b.getCoachRatingCount();
            int cmpCnt = Comparator.<Integer>nullsLast(Comparator.reverseOrder()).compare(cb, ca);
            if (cmpCnt != 0) {
                return cmpCnt;
            }

            return Comparator.<Long>nullsLast(Comparator.reverseOrder()).compare(idb, ida);
        });

        return sorted;
    }

    private List<EquipmentListItem> personalizeEquipments(Long userId, List<EquipmentListItem> equipments) {
        if (userId == null || equipments == null || equipments.isEmpty()) {
            return equipments == null ? List.of() : equipments;
        }

        Set<Long> ids = equipments.stream().map(EquipmentListItem::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return equipments;
        }

        Set<Long> favIds = loadFavoriteTargetIds(userId, "EQUIPMENT", ids);
        if (favIds.isEmpty()) {
            return equipments;
        }

        List<EquipmentListItem> sorted = new ArrayList<>(equipments);
        sorted.sort(
            Comparator
                .<EquipmentListItem>comparingInt(e -> e != null && e.getId() != null && favIds.contains(e.getId()) ? 1 : 0)
                .reversed()
                .thenComparing((EquipmentListItem e) -> e == null ? null : e.getPrice(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e == null ? null : e.getId(), Comparator.nullsLast(Comparator.reverseOrder()))
        );
        return sorted;
    }

    private Set<Long> loadFavoriteTargetIds(Long userId, String targetType, Set<Long> targetIds) {
        if (userId == null || !org.springframework.util.StringUtils.hasText(targetType) || targetIds == null || targetIds.isEmpty()) {
            return Set.of();
        }

        List<Favorite> rows = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getUserId, userId)
            .eq(Favorite::getTargetType, targetType)
            .in(Favorite::getTargetId, targetIds));

        Set<Long> out = new HashSet<>();
        if (rows != null) {
            for (Favorite r : rows) {
                if (r != null && r.getTargetId() != null) {
                    out.add(r.getTargetId());
                }
            }
        }
        return out;
    }

    private List<HomeCourseItem> pickQualityCourses(int size) {
        CoursePageResponse page = courseService.listPublic(1, 100, null, null, null, null);
        List<CourseListItem> items = page == null ? null : page.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Set<Long> coachIds = items.stream()
            .map(CourseListItem::getCoachUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, CoachProfile> coachProfileMap = new HashMap<>();
        if (!coachIds.isEmpty()) {
            List<CoachProfile> profiles = coachProfileMapper.selectBatchIds(coachIds);
            if (profiles != null) {
                for (CoachProfile p : profiles) {
                    if (p != null && p.getUserId() != null) {
                        coachProfileMap.put(p.getUserId(), p);
                    }
                }
            }
        }

        List<HomeCourseItem> out = new ArrayList<>();
        for (CourseListItem c : items) {
            HomeCourseItem it = new HomeCourseItem();
            it.setCourse(c);
            CoachProfile p = c == null ? null : coachProfileMap.get(c.getCoachUserId());
            it.setCoachRatingAvg(p == null ? null : p.getRatingAvg());
            it.setCoachRatingCount(p == null ? null : p.getRatingCount());
            out.add(it);
        }

        out.sort((a, b) -> {
            BigDecimal ra = a == null ? null : a.getCoachRatingAvg();
            BigDecimal rb = b == null ? null : b.getCoachRatingAvg();
            if (ra == null && rb != null) {
                return 1;
            }
            if (ra != null && rb == null) {
                return -1;
            }
            if (ra != null && rb != null) {
                int cmp = rb.compareTo(ra);
                if (cmp != 0) {
                    return cmp;
                }
            }

            Integer ca = a == null ? null : a.getCoachRatingCount();
            Integer cb = b == null ? null : b.getCoachRatingCount();
            int cmpCnt = Comparator.<Integer>nullsLast(Comparator.reverseOrder()).compare(cb, ca);
            if (cmpCnt != 0) {
                return cmpCnt;
            }

            Long ida = a == null || a.getCourse() == null ? null : a.getCourse().getId();
            Long idb = b == null || b.getCourse() == null ? null : b.getCourse().getId();
            return Comparator.<Long>nullsLast(Comparator.reverseOrder()).compare(idb, ida);
        });

        if (out.size() > size) {
            return out.subList(0, size);
        }
        return out;
    }

    private List<EquipmentListItem> pickEquipmentDeals(int size) {
        EquipmentPageResponse page = equipmentCatalogService.listEquipments(1, 100, null, null);
        List<EquipmentListItem> items = page == null ? null : page.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<EquipmentListItem> sorted = new ArrayList<>(items);
        sorted.sort(
            Comparator
                .comparing((EquipmentListItem e) -> e == null ? null : e.getPrice(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e == null ? null : e.getId(), Comparator.nullsLast(Comparator.reverseOrder()))
        );

        if (sorted.size() > size) {
            return sorted.subList(0, size);
        }
        return sorted;
    }

    private int normalizeSize(Integer size, int defaultValue) {
        int s = size == null ? defaultValue : size.intValue();
        if (s < 0) {
            s = defaultValue;
        }
        if (s > 50) {
            s = 50;
        }
        return s;
    }
}
