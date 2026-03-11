package com.communitysport.banner.service;

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
import com.communitysport.banner.dto.HomeBannerCreateRequest;
import com.communitysport.banner.dto.HomeBannerEnabledRequest;
import com.communitysport.banner.dto.HomeBannerItem;
import com.communitysport.banner.dto.HomeBannerUpdateRequest;
import com.communitysport.banner.entity.HomeBanner;
import com.communitysport.banner.mapper.HomeBannerMapper;
import com.communitysport.security.AuthenticatedUser;
import com.communitysport.upload.service.UploadService;

@Service
public class HomeBannerService {

    private final HomeBannerMapper homeBannerMapper;

    private final UploadService uploadService;

    public HomeBannerService(HomeBannerMapper homeBannerMapper, UploadService uploadService) {
        this.homeBannerMapper = homeBannerMapper;
        this.uploadService = uploadService;
    }

    public List<HomeBannerItem> listPublic() {
        LambdaQueryWrapper<HomeBanner> qw = new LambdaQueryWrapper<HomeBanner>()
            .eq(HomeBanner::getEnabled, 1)
            .orderByAsc(HomeBanner::getSortOrder)
            .orderByDesc(HomeBanner::getId);
        List<HomeBanner> rows = homeBannerMapper.selectList(qw);
        List<HomeBannerItem> items = new ArrayList<>();
        for (HomeBanner r : rows) {
            if (r == null) {
                continue;
            }
            items.add(toItem(r));
        }
        return items;
    }

    public List<HomeBannerItem> adminList(Integer enabled) {
        LambdaQueryWrapper<HomeBanner> qw = new LambdaQueryWrapper<HomeBanner>();
        if (enabled != null) {
            qw.eq(HomeBanner::getEnabled, enabled.intValue());
        }
        qw.orderByAsc(HomeBanner::getSortOrder).orderByDesc(HomeBanner::getId);
        List<HomeBanner> rows = homeBannerMapper.selectList(qw);
        List<HomeBannerItem> items = new ArrayList<>();
        for (HomeBanner r : rows) {
            if (r == null) {
                continue;
            }
            items.add(toItem(r));
        }
        return items;
    }

    @Transactional
    public HomeBannerItem create(AuthenticatedUser principal, HomeBannerCreateRequest request) {
        requireUserId(principal);
        if (request == null || !StringUtils.hasText(request.getImageUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageUrl required");
        }

        HomeBanner row = new HomeBanner();
        row.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : null);
        row.setImageUrl(request.getImageUrl().trim());
        row.setLinkUrl(StringUtils.hasText(request.getLinkUrl()) ? request.getLinkUrl().trim() : null);
        row.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder().intValue());
        row.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled().intValue());
        row.setCreatedAt(LocalDateTime.now());
        homeBannerMapper.insert(row);
        return toItem(homeBannerMapper.selectById(row.getId()));
    }

    @Transactional
    public HomeBannerItem update(AuthenticatedUser principal, Long id, HomeBannerUpdateRequest request) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }

        HomeBanner row = homeBannerMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }

        if (request.getTitle() != null) {
            row.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : null);
        }
        if (request.getImageUrl() != null) {
            if (!StringUtils.hasText(request.getImageUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageUrl required");
            }
            row.setImageUrl(request.getImageUrl().trim());
        }
        if (request.getLinkUrl() != null) {
            row.setLinkUrl(StringUtils.hasText(request.getLinkUrl()) ? request.getLinkUrl().trim() : null);
        }
        if (request.getSortOrder() != null) {
            row.setSortOrder(request.getSortOrder().intValue());
        }
        if (request.getEnabled() != null) {
            row.setEnabled(request.getEnabled().intValue());
        }

        homeBannerMapper.updateById(row);
        return toItem(homeBannerMapper.selectById(id));
    }

    @Transactional
    public HomeBannerItem updateEnabled(AuthenticatedUser principal, Long id, HomeBannerEnabledRequest request) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        if (request == null || request.getEnabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled required");
        }

        HomeBanner row = homeBannerMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }

        row.setEnabled(request.getEnabled().intValue());
        homeBannerMapper.updateById(row);
        return toItem(homeBannerMapper.selectById(id));
    }

    @Transactional
    public void delete(AuthenticatedUser principal, Long id) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        homeBannerMapper.deleteById(id);
    }

    @Transactional
    public String uploadAndUpdateImage(AuthenticatedUser principal, Long id, MultipartFile file) {
        requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }
        HomeBanner row = homeBannerMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        }

        String url = uploadService.uploadPhoto("banner", file).getUrl();
        row.setImageUrl(url);
        homeBannerMapper.updateById(row);
        return url;
    }

    private HomeBannerItem toItem(HomeBanner row) {
        if (row == null) {
            return null;
        }
        HomeBannerItem it = new HomeBannerItem();
        it.setId(row.getId());
        it.setTitle(row.getTitle());
        it.setImageUrl(row.getImageUrl());
        it.setLinkUrl(row.getLinkUrl());
        it.setSortOrder(row.getSortOrder());
        it.setEnabled(row.getEnabled());
        return it;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
