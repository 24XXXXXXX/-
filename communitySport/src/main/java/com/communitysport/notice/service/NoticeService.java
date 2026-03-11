package com.communitysport.notice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.notice.dto.NoticeDetailResponse;
import com.communitysport.notice.dto.NoticeListItem;
import com.communitysport.notice.dto.NoticePageResponse;
import com.communitysport.notice.dto.NoticePublishedRequest;
import com.communitysport.notice.dto.NoticeUpsertRequest;
import com.communitysport.notice.entity.Notice;
import com.communitysport.notice.mapper.NoticeMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.upload.service.UploadService;

@Service
public class NoticeService {

    private final NoticeMapper noticeMapper;

    private final SysUserMapper sysUserMapper;

    private final UploadService uploadService;

    public NoticeService(NoticeMapper noticeMapper, SysUserMapper sysUserMapper, UploadService uploadService) {
        this.noticeMapper = noticeMapper;
        this.sysUserMapper = sysUserMapper;
        this.uploadService = uploadService;
    }

    public NoticePageResponse listPublic(Integer page, Integer size, String noticeType, String keyword) {
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

        LambdaQueryWrapper<Notice> countQw = new LambdaQueryWrapper<Notice>()
            .eq(Notice::getPublished, 1);
        if (StringUtils.hasText(noticeType)) {
            countQw.eq(Notice::getNoticeType, noticeType.trim());
        }
        if (StringUtils.hasText(keyword)) {
            countQw.like(Notice::getTitle, keyword.trim());
        }
        long total = noticeMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<NoticeListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<Notice> listQw = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getPublished, 1);
            if (StringUtils.hasText(noticeType)) {
                listQw.eq(Notice::getNoticeType, noticeType.trim());
            }
            if (StringUtils.hasText(keyword)) {
                listQw.like(Notice::getTitle, keyword.trim());
            }
            listQw.orderByDesc(Notice::getPublishAt).orderByDesc(Notice::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<Notice> rows = noticeMapper.selectList(listQw);
            for (Notice n : rows) {
                if (n == null) {
                    continue;
                }
                items.add(toListItem(n));
            }
        }

        NoticePageResponse resp = new NoticePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public NoticeDetailResponse publicDetail(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        Notice row = noticeMapper.selectById(id);
        if (row == null || !Objects.equals(row.getPublished(), 1)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }
        return toDetail(row);
    }

    public NoticePageResponse adminList(Integer page, Integer size, Integer published, String noticeType, String keyword) {
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

        LambdaQueryWrapper<Notice> countQw = new LambdaQueryWrapper<Notice>();
        if (published != null) {
            countQw.eq(Notice::getPublished, published.intValue());
        }
        if (StringUtils.hasText(noticeType)) {
            countQw.eq(Notice::getNoticeType, noticeType.trim());
        }
        if (StringUtils.hasText(keyword)) {
            countQw.like(Notice::getTitle, keyword.trim());
        }
        long total = noticeMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<NoticeListItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<Notice> listQw = new LambdaQueryWrapper<Notice>();
            if (published != null) {
                listQw.eq(Notice::getPublished, published.intValue());
            }
            if (StringUtils.hasText(noticeType)) {
                listQw.eq(Notice::getNoticeType, noticeType.trim());
            }
            if (StringUtils.hasText(keyword)) {
                listQw.like(Notice::getTitle, keyword.trim());
            }
            listQw.orderByDesc(Notice::getPublishAt).orderByDesc(Notice::getId).last("LIMIT " + s + " OFFSET " + offset);
            List<Notice> rows = noticeMapper.selectList(listQw);
            for (Notice n : rows) {
                if (n == null) {
                    continue;
                }
                items.add(toListItem(n));
            }
        }

        NoticePageResponse resp = new NoticePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public NoticeDetailResponse adminDetail(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        Notice row = noticeMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }
        return toDetail(row);
    }

    @Transactional
    public NoticeDetailResponse create(AuthenticatedUser principal, NoticeUpsertRequest request) {
        Long adminId = requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getTitle()) || !StringUtils.hasText(request.getNoticeType())
                || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title/noticeType/content required");
        }

        Notice row = new Notice();
        row.setTitle(request.getTitle().trim());
        row.setNoticeType(request.getNoticeType().trim());
        row.setContent(request.getContent());
        row.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);

        Integer published = request.getPublished() == null ? 1 : request.getPublished().intValue();
        row.setPublished(published);

        LocalDateTime publishAt = request.getPublishAt();
        if (publishAt == null && Objects.equals(published, 1)) {
            publishAt = LocalDateTime.now();
        }
        row.setPublishAt(publishAt);

        row.setPublisherUserId(adminId);
        noticeMapper.insert(row);
        return adminDetail(row.getId());
    }

    @Transactional
    public NoticeDetailResponse update(AuthenticatedUser principal, Long id, NoticeUpsertRequest request) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }
        Notice row = noticeMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }

        if (request.getTitle() != null) {
            if (!StringUtils.hasText(request.getTitle())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title required");
            }
            row.setTitle(request.getTitle().trim());
        }
        if (request.getNoticeType() != null) {
            if (!StringUtils.hasText(request.getNoticeType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "noticeType required");
            }
            row.setNoticeType(request.getNoticeType().trim());
        }
        if (request.getContent() != null) {
            if (!StringUtils.hasText(request.getContent())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
            }
            row.setContent(request.getContent());
        }
        if (request.getCoverUrl() != null) {
            row.setCoverUrl(StringUtils.hasText(request.getCoverUrl()) ? request.getCoverUrl().trim() : null);
        }
        if (request.getPublished() != null) {
            row.setPublished(request.getPublished().intValue());
        }
        if (request.getPublishAt() != null) {
            row.setPublishAt(request.getPublishAt());
        }

        if (Objects.equals(row.getPublished(), 1) && row.getPublishAt() == null) {
            row.setPublishAt(LocalDateTime.now());
        }

        noticeMapper.updateById(row);
        return adminDetail(id);
    }

    @Transactional
    public NoticeDetailResponse updatePublished(AuthenticatedUser principal, Long id, NoticePublishedRequest request) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || request.getPublished() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "published required");
        }

        Notice row = noticeMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }
        row.setPublished(request.getPublished().intValue());
        if (Objects.equals(row.getPublished(), 1) && row.getPublishAt() == null) {
            row.setPublishAt(LocalDateTime.now());
        }
        noticeMapper.updateById(row);
        return adminDetail(id);
    }

    @Transactional
    public void delete(AuthenticatedUser principal, Long id) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        noticeMapper.deleteById(id);
    }

    @Transactional
    public String uploadAndUpdateCover(AuthenticatedUser principal, Long id, MultipartFile file) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        Notice row = noticeMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }

        String url = uploadService.uploadPhoto("notice", file).getUrl();
        row.setCoverUrl(url);
        noticeMapper.updateById(row);
        return url;
    }

    private NoticeListItem toListItem(Notice row) {
        NoticeListItem it = new NoticeListItem();
        it.setId(row.getId());
        it.setTitle(row.getTitle());
        it.setNoticeType(row.getNoticeType());
        it.setCoverUrl(row.getCoverUrl());
        it.setPublished(row.getPublished());
        it.setPublishAt(row.getPublishAt());
        return it;
    }

    private NoticeDetailResponse toDetail(Notice row) {
        NoticeDetailResponse resp = new NoticeDetailResponse();
        resp.setId(row.getId());
        resp.setTitle(row.getTitle());
        resp.setNoticeType(row.getNoticeType());
        resp.setContent(row.getContent());
        resp.setCoverUrl(row.getCoverUrl());
        resp.setPublished(row.getPublished());
        resp.setPublishAt(row.getPublishAt());
        resp.setPublisherUserId(row.getPublisherUserId());

        SysUser u = row.getPublisherUserId() == null ? null : sysUserMapper.selectById(row.getPublisherUserId());
        resp.setPublisherUsername(u == null ? null : u.getUsername());
        return resp;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
