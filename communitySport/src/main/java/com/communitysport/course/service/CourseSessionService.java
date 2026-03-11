package com.communitysport.course.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.course.dto.CourseSessionCreateRequest;
import com.communitysport.course.dto.CourseSessionItem;
import com.communitysport.course.dto.CourseSessionStatusRequest;
import com.communitysport.course.entity.CoachCourse;
import com.communitysport.course.entity.CoachCourseSession;
import com.communitysport.course.mapper.CoachCourseMapper;
import com.communitysport.course.mapper.CoachCourseSessionMapper;
import com.communitysport.security.AuthenticatedUser;

/**
 * 课次（session）服务：负责课次的创建、查询、状态更新等。
 * 
 * @author [Your Name]
 */
@Service
public class CourseSessionService {

    private final CoachCourseMapper coachCourseMapper;

    private final CoachCourseSessionMapper coachCourseSessionMapper;

    public CourseSessionService(CoachCourseMapper coachCourseMapper, CoachCourseSessionMapper coachCourseSessionMapper) {
        this.coachCourseMapper = coachCourseMapper;
        this.coachCourseSessionMapper = coachCourseSessionMapper;
    }

    /**
     * 创建课次：教练端能力，必须登录且必须是该课程的所属教练。
     * 
     * @param principal   登录用户
     * @param courseId    课程 ID
     * @param request     课次创建请求
     * @return 课次信息
     */
    @Transactional
    public CourseSessionItem create(AuthenticatedUser principal, Long courseId, CourseSessionCreateRequest request) {
        // 课次（session）是课程（course）的“具体开班时间段”，也是后续报名/占座的资源载体。
        // 这里的创建接口是教练端能力：必须登录且必须是该课程的所属教练。
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId required");
        }
        if (request == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime/endTime required");
        }
        // 时间区间的基本合法性：开始必须早于结束。
        // 更复杂的“与其他课次冲突/与场馆开放时间冲突”等约束若要做，通常在更上层做规则校验或在 DB 侧加唯一约束。
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }

        CoachCourse course = coachCourseMapper.selectById(courseId);
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        // 权限边界：课次属于课程，课程属于教练；因此教练只能为“自己的课程”创建课次。
        if (!Objects.equals(course.getCoachUserId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        // 容量（capacity）默认策略：
        // - 如果请求没传 capacity，则默认沿用课程层面的 capacity；
        // - 如果课程层也没填，则兜底 1。
        // 这么做的原因是：课程 capacity 代表“常规开班规模”，而个别课次可能需要临时调整。
        int cap = request.getCapacity() == null ? (course.getCapacity() == null ? 1 : course.getCapacity()) : request.getCapacity();
        if (cap < 1 || cap > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity must be 1-1000");
        }

        CoachCourseSession row = new CoachCourseSession();
        row.setCourseId(courseId);
        row.setStartTime(request.getStartTime());
        row.setEndTime(request.getEndTime());
        row.setCapacity(cap);
        // enrolledCount 是“已报名/已占座”数量。初始化为 0。
        // 后续报名时会通过 Mapper 的条件更新（CAS）来做并发占座，避免超卖。
        row.setEnrolledCount(0);
        // 状态机（面向报名/展示的最小集合）：
        // - OPEN：可报名
        // - CLOSED：停止报名，但课次仍存在（可能用于“已报名用户继续核销/查看”）
        // - CANCELED：取消课次（通常需要在 booking 侧做退款/通知的联动）
        row.setStatus("OPEN");
        row.setCreatedAt(LocalDateTime.now());

        coachCourseSessionMapper.insert(row);
        // insert 后再 select 一次：
        // - 有些字段可能由数据库默认值/触发器补齐
        // - 也能确保返回的对象包含自增主键等信息
        CoachCourseSession after = row.getId() == null ? null : coachCourseSessionMapper.selectById(row.getId());
        return toItem(after);
    }

    /**
     * 教练端“我的课次列表”。
     * 
     * @param principal 登录用户
     * @param courseId  课程 ID
     * @param date      日期
     * @param status    状态
     * @return 课次列表
     */
    @Transactional
    public List<CourseSessionItem> mySessions(AuthenticatedUser principal, Long courseId, LocalDate date, String status) {
        // 教练端“我的课次列表”。
        // 典型用途：教练在后台管理某课程的排课、查看某天的课次安排、关闭/取消课次。
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId required");
        }

        CoachCourse course = coachCourseMapper.selectById(courseId);
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        if (!Objects.equals(course.getCoachUserId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        LambdaQueryWrapper<CoachCourseSession> qw = new LambdaQueryWrapper<CoachCourseSession>()
            .eq(CoachCourseSession::getCourseId, courseId)
            .orderByAsc(CoachCourseSession::getStartTime);

        if (date != null) {
            // 日期筛选采用 [date 00:00:00, date+1 00:00:00) 的半开区间：
            // - 避免“23:59:59.999”这类边界问题
            // - 也能自然覆盖当天所有开始时间落在该日的课次
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = date.plusDays(1).atStartOfDay();
            qw.ge(CoachCourseSession::getStartTime, from).lt(CoachCourseSession::getStartTime, to);
        }
        if (StringUtils.hasText(status)) {
            // status 由教练侧决定：
            // - OPEN：继续对外招生
            // - CLOSED：临时停止招生
            // - CANCELED：取消该课次
            qw.eq(CoachCourseSession::getStatus, status);
        }

        List<CoachCourseSession> rows = coachCourseSessionMapper.selectList(qw);
        List<CourseSessionItem> items = new ArrayList<>();
        if (rows != null) {
            for (CoachCourseSession r : rows) {
                items.add(toItem(r));
            }
        }
        return items;
    }

    /**
     * 公开课次列表（用户端）：只允许查看“上架（ON_SALE）课程”的课次。
     * 
     * @param courseId 课程 ID
     * @param date     日期
     * @return 课次列表
     */
    @Transactional
    public List<CourseSessionItem> listPublic(Long courseId, LocalDate date) {
        // 公开课次列表（用户端）：只允许查看“上架（ON_SALE）课程”的课次。
        // - 只展示 OPEN 课次（可报名）
        // 这样可以把教练内部排课（CLOSED/CANCELED 等）与用户可见内容隔离开。
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId required");
        }

        CoachCourse course = coachCourseMapper.selectById(courseId);
        // 公共可见性口径：课程必须存在且处于 ON_SALE。
        // 注意：这里选择返回 404，而不是 403/400，属于“资源不存在/不可见”的统一对外表现。
        if (course == null || !Objects.equals(course.getStatus(), "ON_SALE")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        LambdaQueryWrapper<CoachCourseSession> qw = new LambdaQueryWrapper<CoachCourseSession>()
            .eq(CoachCourseSession::getCourseId, courseId)
            .eq(CoachCourseSession::getStatus, "OPEN")
            .orderByAsc(CoachCourseSession::getStartTime);

        if (date != null) {
            // 与教练端一致的半开区间筛选，保证前后端对“某天课次”的理解一致。
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = date.plusDays(1).atStartOfDay();
            qw.ge(CoachCourseSession::getStartTime, from).lt(CoachCourseSession::getStartTime, to);
        }

        List<CoachCourseSession> rows = coachCourseSessionMapper.selectList(qw);
        List<CourseSessionItem> items = new ArrayList<>();
        if (rows != null) {
            for (CoachCourseSession r : rows) {
                items.add(toItem(r));
            }
        }
        return items;
    }

    /**
     * 教练端修改课次状态：不直接涉及“已报名订单”的处理。
     * 
     * @param principal 登录用户
     * @param sessionId 课次 ID
     * @param request   状态更新请求
     * @return 课次信息
     */
    @Transactional
    public CourseSessionItem updateStatus(AuthenticatedUser principal, Long sessionId, CourseSessionStatusRequest request) {
        // 教练端修改课次状态：不直接涉及“已报名订单”的处理。
        // 订单侧的退款/通知/已占座释放等联动通常在更高层业务（booking service）中完成
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (sessionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }

        String status = request.getStatus().trim();
        // 只允许在约定的枚举集合中取值，防止前端传入任意字符串造成脏数据。
        if (!Objects.equals(status, "OPEN") && !Objects.equals(status, "CLOSED") && !Objects.equals(status, "CANCELED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be OPEN/CLOSED/CANCELED");
        }

        CoachCourseSession row = coachCourseSessionMapper.selectById(sessionId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }

        CoachCourse course = row.getCourseId() == null ? null : coachCourseMapper.selectById(row.getCourseId());
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        // 权限校验：通过 session -> course 追溯到教练归属。
        if (!Objects.equals(course.getCoachUserId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        row.setStatus(status);
        coachCourseSessionMapper.updateById(row);
        return toItem(row);
    }

    private CourseSessionItem toItem(CoachCourseSession row) {
        if (row == null) {
            return null;
        }
        CourseSessionItem item = new CourseSessionItem();
        item.setId(row.getId());
        item.setCourseId(row.getCourseId());
        item.setStartTime(row.getStartTime());
        item.setEndTime(row.getEndTime());
        item.setCapacity(row.getCapacity());
        item.setEnrolledCount(row.getEnrolledCount());
        item.setStatus(row.getStatus());
        return item;
    }
}
