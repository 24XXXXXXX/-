package com.communitysport.consultation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.consultation.dto.ConsultationCreateRequest;
import com.communitysport.consultation.dto.ConsultationDetailResponse;
import com.communitysport.consultation.dto.ConsultationListItem;
import com.communitysport.consultation.dto.ConsultationMessageItem;
import com.communitysport.consultation.dto.ConsultationPageResponse;
import com.communitysport.consultation.dto.ConsultationReplyRequest;
import com.communitysport.consultation.entity.CourseConsultation;
import com.communitysport.consultation.entity.CourseConsultationMessage;
import com.communitysport.consultation.mapper.CourseConsultationMapper;
import com.communitysport.consultation.mapper.CourseConsultationMessageMapper;
import com.communitysport.course.entity.CoachCourse;
import com.communitysport.course.mapper.CoachCourseMapper;
import com.communitysport.message.service.UserMessageService;
import com.communitysport.security.AuthenticatedUser;

@Service
public class CourseConsultationService {

    private final CourseConsultationMapper courseConsultationMapper;

    private final CourseConsultationMessageMapper courseConsultationMessageMapper;

    private final CoachCourseMapper coachCourseMapper;

    private final SysUserMapper sysUserMapper;

    private final UserMessageService userMessageService;

    public CourseConsultationService(
            CourseConsultationMapper courseConsultationMapper,
            CourseConsultationMessageMapper courseConsultationMessageMapper,
            CoachCourseMapper coachCourseMapper,
            SysUserMapper sysUserMapper,
            UserMessageService userMessageService
    ) {
        this.courseConsultationMapper = courseConsultationMapper;
        this.courseConsultationMessageMapper = courseConsultationMessageMapper;
        this.coachCourseMapper = coachCourseMapper;
        this.sysUserMapper = sysUserMapper;
        this.userMessageService = userMessageService;
    }

    @Transactional
    public ConsultationDetailResponse create(AuthenticatedUser principal, ConsultationCreateRequest request) {
        Long userId = requireUserId(principal);
        if (request == null || request.getCourseId() == null || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId/content required");
        }

        CoachCourse course = coachCourseMapper.selectById(request.getCourseId());
        if (course == null || !Objects.equals(course.getStatus(), "ON_SALE") || course.getCoachUserId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        LocalDateTime now = LocalDateTime.now();

        CourseConsultation c = new CourseConsultation();
        c.setConsultationNo(UUID.randomUUID().toString().replace("-", ""));
        c.setCourseId(course.getId());
        c.setUserId(userId);
        c.setCoachUserId(course.getCoachUserId());
        c.setStatus("OPEN");
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        courseConsultationMapper.insert(c);

        CourseConsultationMessage m = new CourseConsultationMessage();
        m.setConsultationId(c.getId());
        m.setSenderUserId(userId);
        m.setSenderRole("USER");
        m.setContent(request.getContent().trim());
        m.setCreatedAt(now);
        courseConsultationMessageMapper.insert(m);

        String title = StringUtils.hasText(course.getTitle()) ? course.getTitle().trim() : "课程";
        userMessageService.createMessage(course.getCoachUserId(), "CONSULTATION", "新的课程咨询", "有用户咨询【" + title + "】。", "COURSE_CONSULTATION", c.getId());

        return toDetail(c, course, loadUser(userId), loadUser(course.getCoachUserId()), loadMessages(c.getId()));
    }

    public ConsultationPageResponse myConsultations(AuthenticatedUser principal, Integer page, Integer size, String status) {
        Long userId = requireUserId(principal);
        return listByUser(page, size, userId, status);
    }

    public ConsultationDetailResponse myDetail(AuthenticatedUser principal, Long id) {
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CourseConsultation c = courseConsultationMapper.selectById(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(c.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        CoachCourse course = c.getCourseId() == null ? null : coachCourseMapper.selectById(c.getCourseId());
        SysUser user = loadUser(c.getUserId());
        SysUser coach = loadUser(c.getCoachUserId());
        return toDetail(c, course, user, coach, loadMessages(c.getId()));
    }

    @Transactional
    public ConsultationMessageItem userAddMessage(AuthenticatedUser principal, Long id, ConsultationReplyRequest request) {
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }

        CourseConsultation c = courseConsultationMapper.selectById(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(c.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!Objects.equals(c.getStatus(), "OPEN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consultation closed");
        }

        LocalDateTime now = LocalDateTime.now();
        CourseConsultationMessage m = new CourseConsultationMessage();
        m.setConsultationId(c.getId());
        m.setSenderUserId(userId);
        m.setSenderRole("USER");
        m.setContent(request.getContent().trim());
        m.setCreatedAt(now);
        courseConsultationMessageMapper.insert(m);

        c.setUpdatedAt(now);
        courseConsultationMapper.updateById(c);

        userMessageService.createMessage(c.getCoachUserId(), "CONSULTATION", "用户追加咨询", "用户对咨询进行了回复。", "COURSE_CONSULTATION", c.getId());

        return toMessageItem(m);
    }

    public ConsultationPageResponse coachConsultations(AuthenticatedUser principal, Integer page, Integer size, String status) {
        Long coachUserId = requireUserId(principal);
        return listByCoach(page, size, coachUserId, status);
    }

    public ConsultationDetailResponse coachDetail(AuthenticatedUser principal, Long id) {
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CourseConsultation c = courseConsultationMapper.selectById(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(c.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        CoachCourse course = c.getCourseId() == null ? null : coachCourseMapper.selectById(c.getCourseId());
        SysUser user = loadUser(c.getUserId());
        SysUser coach = loadUser(c.getCoachUserId());
        return toDetail(c, course, user, coach, loadMessages(c.getId()));
    }

    @Transactional
    public ConsultationMessageItem coachReply(AuthenticatedUser principal, Long id, ConsultationReplyRequest request) {
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }

        CourseConsultation c = courseConsultationMapper.selectById(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(c.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!Objects.equals(c.getStatus(), "OPEN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consultation closed");
        }

        LocalDateTime now = LocalDateTime.now();
        CourseConsultationMessage m = new CourseConsultationMessage();
        m.setConsultationId(c.getId());
        m.setSenderUserId(coachUserId);
        m.setSenderRole("COACH");
        m.setContent(request.getContent().trim());
        m.setCreatedAt(now);
        courseConsultationMessageMapper.insert(m);

        c.setUpdatedAt(now);
        courseConsultationMapper.updateById(c);

        userMessageService.createMessage(c.getUserId(), "CONSULTATION", "教练回复咨询", "教练已回复你的课程咨询。", "COURSE_CONSULTATION", c.getId());

        return toMessageItem(m);
    }

    @Transactional
    public ConsultationDetailResponse coachClose(AuthenticatedUser principal, Long id) {
        Long coachUserId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        CourseConsultation c = courseConsultationMapper.selectById(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(c.getCoachUserId(), coachUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (Objects.equals(c.getStatus(), "CLOSED")) {
            return coachDetail(principal, id);
        }

        c.setStatus("CLOSED");
        c.setUpdatedAt(LocalDateTime.now());
        courseConsultationMapper.updateById(c);

        userMessageService.createMessage(c.getUserId(), "CONSULTATION", "咨询已关闭", "教练已关闭本次咨询。", "COURSE_CONSULTATION", c.getId());

        return coachDetail(principal, id);
    }

    private ConsultationPageResponse listByUser(Integer page, Integer size, Long userId, String status) {
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

        LambdaQueryWrapper<CourseConsultation> countQw = new LambdaQueryWrapper<CourseConsultation>()
            .eq(CourseConsultation::getUserId, userId);
        if (StringUtils.hasText(status)) {
            countQw.eq(CourseConsultation::getStatus, status.trim());
        }
        long total = courseConsultationMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<ConsultationListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CourseConsultation> listQw = new LambdaQueryWrapper<CourseConsultation>()
                .eq(CourseConsultation::getUserId, userId);
            if (StringUtils.hasText(status)) {
                listQw.eq(CourseConsultation::getStatus, status.trim());
            }
            listQw.orderByDesc(CourseConsultation::getUpdatedAt).orderByDesc(CourseConsultation::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<CourseConsultation> rows = courseConsultationMapper.selectList(listQw);
            items = toListItems(rows);
        }

        ConsultationPageResponse resp = new ConsultationPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private ConsultationPageResponse listByCoach(Integer page, Integer size, Long coachUserId, String status) {
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

        LambdaQueryWrapper<CourseConsultation> countQw = new LambdaQueryWrapper<CourseConsultation>()
            .eq(CourseConsultation::getCoachUserId, coachUserId);
        if (StringUtils.hasText(status)) {
            countQw.eq(CourseConsultation::getStatus, status.trim());
        }
        long total = courseConsultationMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<ConsultationListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<CourseConsultation> listQw = new LambdaQueryWrapper<CourseConsultation>()
                .eq(CourseConsultation::getCoachUserId, coachUserId);
            if (StringUtils.hasText(status)) {
                listQw.eq(CourseConsultation::getStatus, status.trim());
            }
            listQw.orderByDesc(CourseConsultation::getUpdatedAt).orderByDesc(CourseConsultation::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<CourseConsultation> rows = courseConsultationMapper.selectList(listQw);
            items = toListItems(rows);
        }

        ConsultationPageResponse resp = new ConsultationPageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    private List<ConsultationListItem> toListItems(List<CourseConsultation> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> courseIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> coachIds = new HashSet<>();
        for (CourseConsultation c : rows) {
            if (c == null) {
                continue;
            }
            if (c.getCourseId() != null) {
                courseIds.add(c.getCourseId());
            }
            if (c.getUserId() != null) {
                userIds.add(c.getUserId());
            }
            if (c.getCoachUserId() != null) {
                coachIds.add(c.getCoachUserId());
            }
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

        Set<Long> allUserIds = new HashSet<>();
        allUserIds.addAll(userIds);
        allUserIds.addAll(coachIds);

        Map<Long, SysUser> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(allUserIds);
            if (users != null) {
                for (SysUser u : users) {
                    if (u != null && u.getId() != null) {
                        userMap.put(u.getId(), u);
                    }
                }
            }
        }

        List<ConsultationListItem> items = new ArrayList<>();
        for (CourseConsultation c : rows) {
            if (c == null) {
                continue;
            }
            ConsultationListItem it = new ConsultationListItem();
            it.setId(c.getId());
            it.setConsultationNo(c.getConsultationNo());
            it.setCourseId(c.getCourseId());
            CoachCourse course = c.getCourseId() == null ? null : courseMap.get(c.getCourseId());
            it.setCourseTitle(course == null ? null : course.getTitle());
            it.setUserId(c.getUserId());
            SysUser u = c.getUserId() == null ? null : userMap.get(c.getUserId());
            it.setUsername(u == null ? null : u.getUsername());
            it.setCoachUserId(c.getCoachUserId());
            SysUser cu = c.getCoachUserId() == null ? null : userMap.get(c.getCoachUserId());
            it.setCoachUsername(cu == null ? null : cu.getUsername());
            it.setStatus(c.getStatus());
            it.setCreatedAt(c.getCreatedAt());
            it.setUpdatedAt(c.getUpdatedAt());
            items.add(it);
        }
        return items;
    }

    private List<ConsultationMessageItem> loadMessages(Long consultationId) {
        if (consultationId == null) {
            return new ArrayList<>();
        }
        List<CourseConsultationMessage> rows = courseConsultationMessageMapper.selectList(new LambdaQueryWrapper<CourseConsultationMessage>()
            .eq(CourseConsultationMessage::getConsultationId, consultationId)
            .orderByAsc(CourseConsultationMessage::getId));

        List<ConsultationMessageItem> items = new ArrayList<>();
        if (rows != null) {
            for (CourseConsultationMessage r : rows) {
                items.add(toMessageItem(r));
            }
        }
        return items;
    }

    private ConsultationMessageItem toMessageItem(CourseConsultationMessage r) {
        ConsultationMessageItem it = new ConsultationMessageItem();
        it.setId(r.getId());
        it.setSenderUserId(r.getSenderUserId());
        it.setSenderRole(r.getSenderRole());
        it.setContent(r.getContent());
        it.setCreatedAt(r.getCreatedAt());
        return it;
    }

    private ConsultationDetailResponse toDetail(
            CourseConsultation c,
            CoachCourse course,
            SysUser user,
            SysUser coach,
            List<ConsultationMessageItem> messages
    ) {
        ConsultationDetailResponse resp = new ConsultationDetailResponse();
        resp.setId(c.getId());
        resp.setConsultationNo(c.getConsultationNo());
        resp.setCourseId(c.getCourseId());
        resp.setCourseTitle(course == null ? null : course.getTitle());
        resp.setUserId(c.getUserId());
        resp.setUsername(user == null ? null : user.getUsername());
        resp.setCoachUserId(c.getCoachUserId());
        resp.setCoachUsername(coach == null ? null : coach.getUsername());
        resp.setStatus(c.getStatus());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setUpdatedAt(c.getUpdatedAt());
        resp.setMessages(messages);
        return resp;
    }

    private SysUser loadUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return sysUserMapper.selectById(userId);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
