package com.communitysport.message.job;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.course.entity.CoachCourse;
import com.communitysport.course.entity.CoachCourseSession;
import com.communitysport.course.mapper.CoachCourseMapper;
import com.communitysport.course.mapper.CoachCourseSessionMapper;
import com.communitysport.coursebooking.entity.CoachCourseBooking;
import com.communitysport.coursebooking.mapper.CoachCourseBookingMapper;
import com.communitysport.message.entity.UserMessage;
import com.communitysport.message.mapper.UserMessageMapper;
import com.communitysport.message.service.UserMessageService;

@Component
public class CourseReminderJob {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CoachCourseSessionMapper coachCourseSessionMapper;

    private final CoachCourseMapper coachCourseMapper;

    private final CoachCourseBookingMapper coachCourseBookingMapper;

    private final UserMessageMapper userMessageMapper;

    private final UserMessageService userMessageService;

    public CourseReminderJob(
            CoachCourseSessionMapper coachCourseSessionMapper,
            CoachCourseMapper coachCourseMapper,
            CoachCourseBookingMapper coachCourseBookingMapper,
            UserMessageMapper userMessageMapper,
            UserMessageService userMessageService
    ) {
        this.coachCourseSessionMapper = coachCourseSessionMapper;
        this.coachCourseMapper = coachCourseMapper;
        this.coachCourseBookingMapper = coachCourseBookingMapper;
        this.userMessageMapper = userMessageMapper;
        this.userMessageService = userMessageService;
    }

    @Scheduled(fixedDelay = 300000)
    public void sendUpcomingCourseReminders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime end = now.plusMinutes(30);

            List<CoachCourseSession> sessions = coachCourseSessionMapper.selectList(new LambdaQueryWrapper<CoachCourseSession>()
                .ge(CoachCourseSession::getStartTime, now)
                .lt(CoachCourseSession::getStartTime, end)
                .ne(CoachCourseSession::getStatus, "CANCELED"));

            if (sessions == null || sessions.isEmpty()) {
                return;
            }

            Map<Long, CoachCourseSession> sessionMap = new HashMap<>();
            Set<Long> sessionIds = new HashSet<>();
            Set<Long> courseIds = new HashSet<>();
            for (CoachCourseSession s : sessions) {
                if (s == null || s.getId() == null) {
                    continue;
                }
                sessionMap.put(s.getId(), s);
                sessionIds.add(s.getId());
                if (s.getCourseId() != null) {
                    courseIds.add(s.getCourseId());
                }
            }
            if (sessionIds.isEmpty()) {
                return;
            }

            Map<Long, CoachCourse> courseMap = new HashMap<>();
            if (!courseIds.isEmpty()) {
                List<CoachCourse> courses = coachCourseMapper.selectBatchIds(courseIds);
                if (courses != null) {
                    for (CoachCourse c : courses) {
                        if (c != null && c.getId() != null) {
                            courseMap.put(c.getId(), c);
                        }
                    }
                }
            }

            List<CoachCourseBooking> bookings = coachCourseBookingMapper.selectList(new LambdaQueryWrapper<CoachCourseBooking>()
                .in(CoachCourseBooking::getCourseSessionId, sessionIds)
                .eq(CoachCourseBooking::getStatus, "PAID")
                .isNull(CoachCourseBooking::getUsedAt));

            if (bookings == null || bookings.isEmpty()) {
                return;
            }

            Set<Long> bookingIds = new HashSet<>();
            for (CoachCourseBooking b : bookings) {
                if (b != null && b.getId() != null) {
                    bookingIds.add(b.getId());
                }
            }
            if (bookingIds.isEmpty()) {
                return;
            }

            List<UserMessage> existing = userMessageMapper.selectList(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getMsgType, "COURSE_REMINDER")
                .eq(UserMessage::getRefType, "COACH_COURSE_BOOKING")
                .in(UserMessage::getRefId, bookingIds));

            Set<Long> remindedBookingIds = new HashSet<>();
            if (existing != null) {
                for (UserMessage m : existing) {
                    if (m != null && m.getRefId() != null) {
                        remindedBookingIds.add(m.getRefId());
                    }
                }
            }

            for (CoachCourseBooking b : bookings) {
                if (b == null || b.getId() == null || b.getUserId() == null) {
                    continue;
                }
                if (remindedBookingIds.contains(b.getId())) {
                    continue;
                }

                CoachCourseSession s = b.getCourseSessionId() == null ? null : sessionMap.get(b.getCourseSessionId());
                if (s == null || s.getStartTime() == null) {
                    continue;
                }

                CoachCourse c = s.getCourseId() == null ? null : courseMap.get(s.getCourseId());
                String title = c == null || !org.springframework.util.StringUtils.hasText(c.getTitle()) ? "课程" : c.getTitle();
                String when = FMT.format(s.getStartTime());
                String content = "你预约的【" + title + "】将于 " + when + " 开始，请提前到达。";

                userMessageService.createMessage(b.getUserId(), "COURSE_REMINDER", "上课提醒", content, "COACH_COURSE_BOOKING", b.getId());
            }
        } catch (Exception ignored) {
        }
    }
}
