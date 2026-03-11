package com.communitysport.venue.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.venue.dto.VenueTimeslotGenerateRequest;
import com.communitysport.venue.dto.VenueTimeslotGenerateResponse;
import com.communitysport.venue.dto.VenueTimeslotItem;
import com.communitysport.venue.entity.Venue;
import com.communitysport.venue.entity.VenueTimeslot;
import com.communitysport.venue.mapper.VenueMapper;
import com.communitysport.venue.mapper.VenueTimeslotMapper;

/**
 * 场地可预约时段服务。
 *
 * <p>Timeslot 是“可被预约的时间片”，核心字段：
 * <p>- startTime/endTime：起止时间
 * <p>- price：该时间片的价格（最小单位整数）
 * <p>- status：AVAILABLE（可约）/BOOKED（已约）/BLOCKED（封禁）
 *
 * <p>与订单（venue_booking）的关系：
 * <p>- 用户下单时会把 timeslot 从 AVAILABLE 原子更新为 BOOKED
 * <p>- 取消/退款时会尝试把 BOOKED 改回 AVAILABLE（如果一致）
 */
@Service
public class VenueTimeslotService {

    private final VenueMapper venueMapper;

    private final VenueTimeslotMapper venueTimeslotMapper;

    public VenueTimeslotService(VenueMapper venueMapper, VenueTimeslotMapper venueTimeslotMapper) {
        this.venueMapper = venueMapper;
        this.venueTimeslotMapper = venueTimeslotMapper;
    }

    public List<VenueTimeslotItem> listByDate(
            Long venueId,
            LocalDate date,
            String status,
            LocalTime startTime,
            LocalTime endTime,
            Integer minPrice,
            Integer maxPrice
    ) {
        // 按日期列出某个场地的时段。
        //
        // 过滤能力：
        // - status：筛选 AVAILABLE/BOOKED/BLOCKED
        // - startTime/endTime：筛选某个时间范围内的时段
        // - minPrice/maxPrice：按价格区间筛选
        if (venueId == null || date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId/date required");
        }

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        LocalDateTime startAt = null;
        LocalDateTime endAt = null;
        if (startTime != null) {
            startAt = date.atTime(startTime);
        }
        if (endTime != null) {
            endAt = date.atTime(endTime);
        }
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }
        if (minPrice != null && minPrice.intValue() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minPrice invalid");
        }
        if (maxPrice != null && maxPrice.intValue() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxPrice invalid");
        }
        if (minPrice != null && maxPrice != null && minPrice.intValue() > maxPrice.intValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minPrice must be <= maxPrice");
        }

        LambdaQueryWrapper<VenueTimeslot> qw = new LambdaQueryWrapper<VenueTimeslot>()
                .eq(VenueTimeslot::getVenueId, venueId)
                .ge(VenueTimeslot::getStartTime, from)
                .lt(VenueTimeslot::getStartTime, to)
                .orderByAsc(VenueTimeslot::getStartTime);

        if (StringUtils.hasText(status)) {
            qw.eq(VenueTimeslot::getStatus, status);
        }

        if (startAt != null) {
            qw.ge(VenueTimeslot::getStartTime, startAt);
        }
        if (endAt != null) {
            qw.lt(VenueTimeslot::getStartTime, endAt);
        }
        if (minPrice != null) {
            qw.ge(VenueTimeslot::getPrice, minPrice.intValue());
        }
        if (maxPrice != null) {
            qw.le(VenueTimeslot::getPrice, maxPrice.intValue());
        }

        List<VenueTimeslot> rows = venueTimeslotMapper.selectList(qw);
        List<VenueTimeslotItem> items = new ArrayList<>();
        if (rows != null) {
            for (VenueTimeslot r : rows) {
                items.add(toItem(r));
            }
        }
        items.sort(Comparator.comparing(VenueTimeslotItem::getStartTime));
        return items;
    }

    @Transactional
    public VenueTimeslotGenerateResponse generate(Long venueId, VenueTimeslotGenerateRequest request) {
        // 后台批量生成某天时段：
        // - 读取 venue.price_per_hour 作为“小时价”
        // - 按 slotMinutes 切分一天的时间范围
        // - 用 (start,end) 作为 key 去重，避免重复生成
        //
        // 注意：
        // - slotMinutes 要求 30 的倍数，便于展示与排班
        // - 价格按“向上取整”计算（CEILING），避免出现 0.5 元之类的小数
        if (venueId == null || request == null || request.getDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId/date required");
        }

        int startHour = request.getStartHour() == null ? 9 : request.getStartHour();
        int endHour = request.getEndHour() == null ? 22 : request.getEndHour();
        int slotMinutes = request.getSlotMinutes() == null ? 60 : request.getSlotMinutes();

        if (startHour < 0 || startHour > 23 || endHour < 1 || endHour > 24 || startHour >= endHour) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid startHour/endHour");
        }
        if (slotMinutes < 30 || slotMinutes > 240 || slotMinutes % 30 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid slotMinutes");
        }

        Venue venue = venueMapper.selectById(venueId);
        if (venue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }

        int pricePerHour = venue.getPricePerHour() == null ? 0 : venue.getPricePerHour();
        int slotPrice = calcSlotPrice(pricePerHour, slotMinutes);

        LocalDate date = request.getDate();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<VenueTimeslot> existing = venueTimeslotMapper.selectList(new LambdaQueryWrapper<VenueTimeslot>()
                .eq(VenueTimeslot::getVenueId, venueId)
                .ge(VenueTimeslot::getStartTime, dayStart)
                .lt(VenueTimeslot::getStartTime, dayEnd));

        Set<String> existsKey = new HashSet<>();
        if (existing != null) {
            for (VenueTimeslot e : existing) {
                if (e == null || e.getStartTime() == null || e.getEndTime() == null) {
                    continue;
                }
                existsKey.add(key(e.getStartTime(), e.getEndTime()));
            }
        }

        LocalDateTime start = date.atTime(startHour, 0);
        LocalDateTime end = date.atTime(endHour == 24 ? 23 : endHour, endHour == 24 ? 59 : 0);
        if (endHour == 24) {
            end = date.plusDays(1).atStartOfDay();
        }

        int created = 0;
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusMinutes(slotMinutes);
            if (next.isAfter(end)) {
                break;
            }

            String k = key(cursor, next);
            if (!existsKey.contains(k)) {
                VenueTimeslot row = new VenueTimeslot();
                row.setVenueId(venueId);
                row.setStartTime(cursor);
                row.setEndTime(next);
                row.setPrice(slotPrice);
                row.setStatus("AVAILABLE");

                venueTimeslotMapper.insert(row);
                created++;
                existsKey.add(k);
            }

            cursor = next;
        }

        List<VenueTimeslotItem> items = listByDate(venueId, date, null, null, null, null, null);

        VenueTimeslotGenerateResponse resp = new VenueTimeslotGenerateResponse();
        resp.setVenueId(venueId);
        resp.setDate(date);
        resp.setCreatedCount(created);
        resp.setTotal(items.size());
        resp.setItems(items);
        return resp;
    }

    @Transactional
    public VenueTimeslotItem updateStatus(Long id, String status) {
        // 手动修改时段状态：
        // - 只能在 AVAILABLE/BLOCKED 之间切换
        // - 如果已经 BOOKED，不允许修改（否则会导致订单与时段状态不一致）
        if (id == null || !StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id/status required");
        }
        if (!Objects.equals(status, "AVAILABLE") && !Objects.equals(status, "BLOCKED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }

        VenueTimeslot row = venueTimeslotMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeslot not found");
        }
        if (Objects.equals(row.getStatus(), "BOOKED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BOOKED timeslot cannot be updated");
        }

        row.setStatus(status);
        venueTimeslotMapper.updateById(row);
        return toItem(row);
    }

    private VenueTimeslotItem toItem(VenueTimeslot row) {
        VenueTimeslotItem item = new VenueTimeslotItem();
        item.setId(row.getId());
        item.setVenueId(row.getVenueId());
        item.setStartTime(row.getStartTime());
        item.setEndTime(row.getEndTime());
        item.setPrice(row.getPrice());
        item.setStatus(row.getStatus());
        return item;
    }

    private String key(LocalDateTime start, LocalDateTime end) {
        return start.toString() + "|" + end.toString();
    }

    private int calcSlotPrice(int pricePerHour, int slotMinutes) {
        // 把“小时价”换算成“时段价”：
        // price = pricePerHour * slotMinutes / 60，并向上取整。
        if (pricePerHour <= 0) {
            return 0;
        }
        BigDecimal p = BigDecimal.valueOf(pricePerHour);
        BigDecimal m = BigDecimal.valueOf(slotMinutes);
        BigDecimal price = p.multiply(m).divide(BigDecimal.valueOf(60), 0, RoundingMode.CEILING);
        return price.intValue();
    }
}
