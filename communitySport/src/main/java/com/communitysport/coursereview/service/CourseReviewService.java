package com.communitysport.coursereview.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.coach.mapper.CoachProfileMapper;
import com.communitysport.course.entity.CoachCourse;
import com.communitysport.course.entity.CoachCourseSession;
import com.communitysport.course.mapper.CoachCourseMapper;
import com.communitysport.course.mapper.CoachCourseSessionMapper;
import com.communitysport.coursebooking.entity.CoachCourseBooking;
import com.communitysport.coursebooking.mapper.CoachCourseBookingMapper;
import com.communitysport.coursereview.dto.CourseReviewCreateRequest;
import com.communitysport.coursereview.dto.CourseReviewListItem;
import com.communitysport.coursereview.dto.CourseReviewPageResponse;
import com.communitysport.coursereview.entity.CourseReview;
import com.communitysport.coursereview.mapper.CourseReviewMapper;
import com.communitysport.security.AuthenticatedUser;

@Service
public class CourseReviewService {

    private final CourseReviewMapper courseReviewMapper;

    private final CoachCourseBookingMapper coachCourseBookingMapper;

    private final CoachCourseSessionMapper coachCourseSessionMapper;

    private final CoachCourseMapper coachCourseMapper;

    private final SysUserMapper sysUserMapper;

    private final CoachProfileMapper coachProfileMapper;

    public CourseReviewService(
            CourseReviewMapper courseReviewMapper,
            CoachCourseBookingMapper coachCourseBookingMapper,
            CoachCourseSessionMapper coachCourseSessionMapper,
            CoachCourseMapper coachCourseMapper,
            SysUserMapper sysUserMapper,
            CoachProfileMapper coachProfileMapper
    ) {
        this.courseReviewMapper = courseReviewMapper;
        this.coachCourseBookingMapper = coachCourseBookingMapper;
        this.coachCourseSessionMapper = coachCourseSessionMapper;
        this.coachCourseMapper = coachCourseMapper;
        this.sysUserMapper = sysUserMapper;
        this.coachProfileMapper = coachProfileMapper;
    }

    @Transactional
    public CourseReviewListItem create(AuthenticatedUser principal, CourseReviewCreateRequest request) {
        Long userId = requireUserId(principal);
        if (request == null || request.getBookingId() == null || request.getRating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId/rating required");
        }

        int rating = request.getRating().intValue();
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating must be 1-5");
        }

        CoachCourseBooking booking = coachCourseBookingMapper.selectById(request.getBookingId());
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
        long exists = courseReviewMapper.selectCount(new LambdaQueryWrapper<CourseReview>().eq(CourseReview::getBookingId, bookingId));
        if (exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reviewed");
        }

        Long sessionId = booking.getCourseSessionId();
        CoachCourseSession session = sessionId == null ? null : coachCourseSessionMapper.selectById(sessionId);
        if (session == null || session.getCourseId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }

        CoachCourse course = coachCourseMapper.selectById(session.getCourseId());
        if (course == null || course.getCoachUserId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
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

        CourseReview row = new CourseReview();
        row.setCourseId(course.getId());
        row.setCoachUserId(course.getCoachUserId());
        row.setUserId(userId);
        row.setBookingId(bookingId);
        row.setRating(rating);
        row.setContent(content);
        row.setCreatedAt(LocalDateTime.now());

        try {
            courseReviewMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reviewed");
        }

        try {
            coachProfileMapper.addRating(course.getCoachUserId(), rating);
        } catch (Exception ignored) {
        }

        CourseReview after = row.getId() == null ? null : courseReviewMapper.selectById(row.getId());
        return toItem(after);
    }

    public CourseReviewPageResponse listByCourse(Long courseId, Integer page, Integer size) {
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId required");
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

        LambdaQueryWrapper<CourseReview> countQw = new LambdaQueryWrapper<CourseReview>().eq(CourseReview::getCourseId, courseId);
        long total = courseReviewMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<CourseReviewListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CourseReview> listQw = new LambdaQueryWrapper<CourseReview>()
                .eq(CourseReview::getCourseId, courseId)
                .orderByDesc(CourseReview::getId)
                .last("LIMIT " + s + " OFFSET " + offset);

            List<CourseReview> rows = courseReviewMapper.selectList(listQw);

            Set<Long> userIds = rows.stream().map(CourseReview::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> userMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
                if (users != null) {
                    for (SysUser u : users) {
                        if (u != null && u.getId() != null) {
                            userMap.put(u.getId(), u.getUsername());
                        }
                    }
                }
            }

            for (CourseReview r : rows) {
                CourseReviewListItem item = new CourseReviewListItem();
                item.setId(r.getId());
                item.setCourseId(r.getCourseId());
                item.setCoachUserId(r.getCoachUserId());
                item.setUserId(r.getUserId());
                item.setUsername(r.getUserId() == null ? null : userMap.get(r.getUserId()));
                item.setBookingId(r.getBookingId());
                item.setRating(r.getRating());
                item.setContent(r.getContent());
                item.setCreatedAt(r.getCreatedAt());
                items.add(item);
            }
        }

        CourseReviewPageResponse resp = new CourseReviewPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private CourseReviewListItem toItem(CourseReview r) {
        if (r == null) {
            return null;
        }

        CourseReviewListItem item = new CourseReviewListItem();
        item.setId(r.getId());
        item.setCourseId(r.getCourseId());
        item.setCoachUserId(r.getCoachUserId());
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
