package com.communitysport.favorite.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.course.entity.CoachCourse;
import com.communitysport.course.mapper.CoachCourseMapper;
import com.communitysport.equipment.entity.Equipment;
import com.communitysport.equipment.mapper.EquipmentMapper;
import com.communitysport.favorite.dto.FavoriteCountResponse;
import com.communitysport.favorite.dto.FavoriteListItem;
import com.communitysport.favorite.dto.FavoritePageResponse;
import com.communitysport.favorite.dto.FavoriteStatusResponse;
import com.communitysport.favorite.dto.FavoriteToggleRequest;
import com.communitysport.favorite.entity.Favorite;
import com.communitysport.favorite.mapper.FavoriteMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.mapper.VenueMapper;
import com.communitysport.video.entity.CoachVideo;
import com.communitysport.video.mapper.CoachVideoMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FavoriteService {

    private static final Set<String> ALLOWED_TYPES = Set.of("VENUE", "COACH", "COURSE", "EQUIPMENT", "VIDEO");

    private final FavoriteMapper favoriteMapper;

    private final VenueMapper venueMapper;

    private final SysUserMapper sysUserMapper;

    private final CoachCourseMapper coachCourseMapper;

    private final EquipmentMapper equipmentMapper;

    private final CoachVideoMapper coachVideoMapper;

    private final ObjectMapper objectMapper;

    public FavoriteService(
            FavoriteMapper favoriteMapper,
            VenueMapper venueMapper,
            SysUserMapper sysUserMapper,
            CoachCourseMapper coachCourseMapper,
            EquipmentMapper equipmentMapper,
            CoachVideoMapper coachVideoMapper,
            ObjectMapper objectMapper
    ) {
        this.favoriteMapper = favoriteMapper;
        this.venueMapper = venueMapper;
        this.sysUserMapper = sysUserMapper;
        this.coachCourseMapper = coachCourseMapper;
        this.equipmentMapper = equipmentMapper;
        this.coachVideoMapper = coachVideoMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FavoriteStatusResponse favorite(AuthenticatedUser principal, FavoriteToggleRequest request) {
        Long userId = requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getTargetType()) || request.getTargetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetType/targetId required");
        }

        String type = normalizeType(request.getTargetType());
        Long targetId = request.getTargetId();
        validateTargetExists(type, targetId);

        Favorite row = new Favorite();
        row.setUserId(userId);
        row.setTargetType(type);
        row.setTargetId(targetId);
        row.setCreatedAt(LocalDateTime.now());

        try {
            favoriteMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            Favorite exists = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, type)
                .eq(Favorite::getTargetId, targetId)
            );
            FavoriteStatusResponse resp = new FavoriteStatusResponse();
            resp.setTargetType(type);
            resp.setTargetId(targetId);
            resp.setFavorited(true);
            resp.setFavoriteId(exists == null ? null : exists.getId());
            return resp;
        }

        FavoriteStatusResponse resp = new FavoriteStatusResponse();
        resp.setTargetType(type);
        resp.setTargetId(targetId);
        resp.setFavorited(true);
        resp.setFavoriteId(row.getId());
        return resp;
    }

    @Transactional
    public FavoriteStatusResponse unfavorite(AuthenticatedUser principal, String targetType, Long targetId) {
        Long userId = requireUserId(principal);
        String type = normalizeType(targetType);
        if (targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetId required");
        }

        Favorite exists = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getUserId, userId)
            .eq(Favorite::getTargetType, type)
            .eq(Favorite::getTargetId, targetId)
        );

        if (exists != null) {
            favoriteMapper.deleteById(exists.getId());
        }

        FavoriteStatusResponse resp = new FavoriteStatusResponse();
        resp.setTargetType(type);
        resp.setTargetId(targetId);
        resp.setFavorited(false);
        resp.setFavoriteId(null);
        return resp;
    }

    public FavoriteStatusResponse status(AuthenticatedUser principal, String targetType, Long targetId) {
        Long userId = requireUserId(principal);
        String type = normalizeType(targetType);
        if (targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetId required");
        }

        Favorite exists = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getUserId, userId)
            .eq(Favorite::getTargetType, type)
            .eq(Favorite::getTargetId, targetId)
        );

        FavoriteStatusResponse resp = new FavoriteStatusResponse();
        resp.setTargetType(type);
        resp.setTargetId(targetId);
        resp.setFavorited(exists != null);
        resp.setFavoriteId(exists == null ? null : exists.getId());
        return resp;
    }

    public FavoriteCountResponse count(String targetType, Long targetId) {
        String type = normalizeType(targetType);
        if (targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetId required");
        }

        long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getTargetType, type)
            .eq(Favorite::getTargetId, targetId)
        );

        FavoriteCountResponse resp = new FavoriteCountResponse();
        resp.setTargetType(type);
        resp.setTargetId(targetId);
        resp.setCount(cnt);
        return resp;
    }

    public FavoritePageResponse myFavorites(AuthenticatedUser principal, Integer page, Integer size, String targetType) {
        Long userId = requireUserId(principal);

        int p = page == null ? 1 : page.intValue();
        int s = size == null ? 20 : size.intValue();
        if (p < 1) {
            p = 1;
        }
        if (s < 1) {
            s = 1;
        }
        if (s > 100) {
            s = 100;
        }

        String type = null;
        if (StringUtils.hasText(targetType)) {
            type = normalizeType(targetType);
        }

        LambdaQueryWrapper<Favorite> countQw = new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getUserId, userId);
        if (type != null) {
            countQw.eq(Favorite::getTargetType, type);
        }
        long total = favoriteMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<FavoriteListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<Favorite> listQw = new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId);
            if (type != null) {
                listQw.eq(Favorite::getTargetType, type);
            }
            listQw.orderByDesc(Favorite::getId).last("LIMIT " + s + " OFFSET " + offset);

            List<Favorite> rows = favoriteMapper.selectList(listQw);
            items = toListItemsWithEnrichment(rows);
        }

        FavoritePageResponse resp = new FavoritePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private List<FavoriteListItem> toListItemsWithEnrichment(List<Favorite> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<FavoriteListItem> items = new ArrayList<>();
        Map<String, Set<Long>> typeIds = new HashMap<>();
        for (Favorite r : rows) {
            if (r == null) {
                continue;
            }
            FavoriteListItem it = new FavoriteListItem();
            it.setId(r.getId());
            it.setTargetType(r.getTargetType());
            it.setTargetId(r.getTargetId());
            it.setCreatedAt(r.getCreatedAt());
            items.add(it);

            if (StringUtils.hasText(r.getTargetType()) && r.getTargetId() != null) {
                typeIds.computeIfAbsent(r.getTargetType(), k -> new HashSet<>()).add(r.getTargetId());
            }
        }

        Map<Long, Venue> venueMap = new HashMap<>();
        Set<Long> venueIds = typeIds.get("VENUE");
        if (venueIds != null && !venueIds.isEmpty()) {
            List<Venue> venues = venueMapper.selectByIds(venueIds);
            if (venues != null) {
                for (Venue v : venues) {
                    if (v != null && v.getId() != null) {
                        venueMap.put(v.getId(), v);
                    }
                }
            }
        }

        Map<Long, Equipment> equipmentMap = new HashMap<>();
        Set<Long> equipmentIds = typeIds.get("EQUIPMENT");
        if (equipmentIds != null && !equipmentIds.isEmpty()) {
            List<Equipment> equipments = equipmentMapper.selectByIds(equipmentIds);
            if (equipments != null) {
                for (Equipment e : equipments) {
                    if (e != null && e.getId() != null) {
                        equipmentMap.put(e.getId(), e);
                    }
                }
            }
        }

        Map<Long, CoachCourse> courseMap = new HashMap<>();
        Set<Long> courseIds = typeIds.get("COURSE");
        if (courseIds != null && !courseIds.isEmpty()) {
            List<CoachCourse> courses = coachCourseMapper.selectByIds(courseIds);
            if (courses != null) {
                for (CoachCourse c : courses) {
                    if (c != null && c.getId() != null) {
                        courseMap.put(c.getId(), c);
                    }
                }
            }
        }

        Map<Long, CoachVideo> videoMap = new HashMap<>();
        Set<Long> videoIds = typeIds.get("VIDEO");
        if (videoIds != null && !videoIds.isEmpty()) {
            List<CoachVideo> videos = coachVideoMapper.selectByIds(videoIds);
            if (videos != null) {
                for (CoachVideo v : videos) {
                    if (v != null && v.getId() != null) {
                        videoMap.put(v.getId(), v);
                    }
                }
            }
        }

        Map<Long, SysUser> userMap = new HashMap<>();
        Set<Long> coachIds = typeIds.get("COACH");
        if (coachIds != null && !coachIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(coachIds);
            if (users != null) {
                for (SysUser u : users) {
                    if (u != null && u.getId() != null) {
                        userMap.put(u.getId(), u);
                    }
                }
            }
        }

        for (FavoriteListItem it : items) {
            if (it.getTargetId() == null || !StringUtils.hasText(it.getTargetType())) {
                continue;
            }
            String t = it.getTargetType();
            Long id = it.getTargetId();
            if (Objects.equals(t, "VENUE")) {
                Venue v = venueMap.get(id);
                it.setTitle(v == null ? null : v.getName());
                String raw = v == null ? null : v.getCoverUrl();
                it.setCoverUrl(pickFirstCoverUrl(raw, parseCoverUrls(raw)));
            } else if (Objects.equals(t, "EQUIPMENT")) {
                Equipment e = equipmentMap.get(id);
                it.setTitle(e == null ? null : e.getName());
                it.setCoverUrl(e == null ? null : e.getCoverUrl());
            } else if (Objects.equals(t, "COURSE")) {
                CoachCourse c = courseMap.get(id);
                it.setTitle(c == null ? null : c.getTitle());
                it.setCoverUrl(c == null ? null : c.getCoverUrl());
            } else if (Objects.equals(t, "VIDEO")) {
                CoachVideo v = videoMap.get(id);
                it.setTitle(v == null ? null : v.getTitle());
                it.setCoverUrl(v == null ? null : v.getCoverUrl());
            } else if (Objects.equals(t, "COACH")) {
                SysUser u = userMap.get(id);
                String title = null;
                if (u != null) {
                    title = StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername();
                }
                it.setTitle(title);
                it.setCoverUrl(u == null ? null : u.getAvatarUrl());
            }
        }

        return items;
    }

    private List<String> parseCoverUrls(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return List.of();
        }
        String s = coverUrl.trim();
        if (!s.startsWith("[")) {
            return List.of();
        }
        try {
            List<String> urls = objectMapper.readValue(s, new TypeReference<List<String>>() {});
            return urls == null ? List.of() : urls;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String pickFirstCoverUrl(String raw, List<String> parsed) {
        if (parsed != null && !parsed.isEmpty() && StringUtils.hasText(parsed.get(0))) {
            return parsed.get(0);
        }
        return raw;
    }

    private void validateTargetExists(String type, Long targetId) {
        if (Objects.equals(type, "VENUE")) {
            Venue v = venueMapper.selectById(targetId);
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target not found");
            }
        } else if (Objects.equals(type, "EQUIPMENT")) {
            Equipment e = equipmentMapper.selectById(targetId);
            if (e == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target not found");
            }
        } else if (Objects.equals(type, "COURSE")) {
            CoachCourse c = coachCourseMapper.selectById(targetId);
            if (c == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target not found");
            }
        } else if (Objects.equals(type, "VIDEO")) {
            CoachVideo v = coachVideoMapper.selectById(targetId);
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target not found");
            }
        } else if (Objects.equals(type, "COACH")) {
            SysUser u = sysUserMapper.selectById(targetId);
            if (u == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target not found");
            }
        }
    }

    private String normalizeType(String input) {
        if (!StringUtils.hasText(input)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetType required");
        }
        String type = input.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetType invalid");
        }
        return type;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
