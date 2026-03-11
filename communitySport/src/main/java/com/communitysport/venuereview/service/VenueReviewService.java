package com.communitysport.venuereview.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.communitysport.booking.entity.VenueBooking;
import com.communitysport.booking.mapper.VenueBookingMapper;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.mapper.VenueMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.venuereview.dto.VenueReviewCreateRequest;
import com.communitysport.venuereview.dto.VenueReviewListItem;
import com.communitysport.venuereview.dto.VenueReviewPageResponse;
import com.communitysport.venuereview.entity.VenueReview;
import com.communitysport.venuereview.mapper.VenueReviewMapper;

@Service
public class VenueReviewService {

    private final VenueReviewMapper venueReviewMapper;

    private final VenueBookingMapper venueBookingMapper;

    private final VenueMapper venueMapper;

    private final SysUserMapper sysUserMapper;

    public VenueReviewService(
            VenueReviewMapper venueReviewMapper,
            VenueBookingMapper venueBookingMapper,
            VenueMapper venueMapper,
            SysUserMapper sysUserMapper
    ) {
        this.venueReviewMapper = venueReviewMapper;
        this.venueBookingMapper = venueBookingMapper;
        this.venueMapper = venueMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Transactional
    public VenueReviewListItem create(AuthenticatedUser principal, VenueReviewCreateRequest request) {
        Long userId = requireUserId(principal);
        if (request == null || request.getBookingId() == null || request.getRating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId/rating required");
        }

        int rating = request.getRating().intValue();
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating must be 1-5");
        }

        VenueBooking booking = venueBookingMapper.selectById(request.getBookingId());
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!Objects.equals(booking.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!Objects.equals(booking.getStatus(), "USED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking not finished");
        }

        Long bookingId = booking.getId();
        long exists = venueReviewMapper.selectCount(new LambdaQueryWrapper<VenueReview>().eq(VenueReview::getBookingId, bookingId));
        if (exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reviewed");
        }

        Long venueId = booking.getVenueId();
        if (venueId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        Venue venue = venueMapper.selectById(venueId);
        if (venue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }

        String content = request.getContent();
        if (content != null) {
            content = content.trim();
            if (!StringUtils.hasText(content)) {
                content = null;
            }
            if (content != null && content.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content too long");
            }
        }

        VenueReview row = new VenueReview();
        row.setVenueId(venue.getId());
        row.setUserId(userId);
        row.setBookingId(bookingId);
        row.setRating(rating);
        row.setContent(content);
        row.setCreatedAt(LocalDateTime.now());

        try {
            venueReviewMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reviewed");
        }

        VenueReview after = row.getId() == null ? null : venueReviewMapper.selectById(row.getId());
        return toItem(after);
    }

    public VenueReviewPageResponse listByVenue(Long venueId, Integer page, Integer size) {
        if (venueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId required");
        }

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

        LambdaQueryWrapper<VenueReview> countQw = new LambdaQueryWrapper<VenueReview>().eq(VenueReview::getVenueId, venueId);
        long total = venueReviewMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<VenueReviewListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<VenueReview> listQw = new LambdaQueryWrapper<VenueReview>()
                .eq(VenueReview::getVenueId, venueId)
                .orderByDesc(VenueReview::getId)
                .last("LIMIT " + s + " OFFSET " + offset);

            List<VenueReview> rows = venueReviewMapper.selectList(listQw);

            Set<Long> userIds = new LinkedHashSet<>();
            if (rows != null) {
                for (VenueReview r : rows) {
                    if (r != null && r.getUserId() != null) {
                        userIds.add(r.getUserId());
                    }
                }
            }

            Map<Long, String> userMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<SysUser> users = sysUserMapper.selectByIds(userIds);
                if (users != null) {
                    for (SysUser u : users) {
                        if (u != null && u.getId() != null) {
                            userMap.put(u.getId(), u.getUsername());
                        }
                    }
                }
            }

            if (rows != null) {
                for (VenueReview r : rows) {
                    if (r == null) {
                        continue;
                    }
                    VenueReviewListItem item = new VenueReviewListItem();
                    item.setId(r.getId());
                    item.setVenueId(r.getVenueId());
                    item.setUserId(r.getUserId());
                    item.setUsername(r.getUserId() == null ? null : userMap.get(r.getUserId()));
                    item.setBookingId(r.getBookingId());
                    item.setRating(r.getRating());
                    item.setContent(r.getContent());
                    item.setCreatedAt(r.getCreatedAt());
                    items.add(item);
                }
            }
        }

        VenueReviewPageResponse resp = new VenueReviewPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private VenueReviewListItem toItem(VenueReview r) {
        if (r == null) {
            return null;
        }

        VenueReviewListItem item = new VenueReviewListItem();
        item.setId(r.getId());
        item.setVenueId(r.getVenueId());
        item.setUserId(r.getUserId());
        if (r.getUserId() != null) {
            SysUser u = sysUserMapper.selectById(r.getUserId());
            item.setUsername(u == null ? null : u.getUsername());
        }
        item.setBookingId(r.getBookingId());
        item.setRating(r.getRating());
        item.setContent(r.getContent());
        item.setCreatedAt(r.getCreatedAt());
        return item;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
