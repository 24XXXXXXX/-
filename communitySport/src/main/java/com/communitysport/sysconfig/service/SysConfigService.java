package com.communitysport.sysconfig.service;

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
import com.communitysport.sysconfig.dto.SysConfigItem;
import com.communitysport.sysconfig.dto.SysConfigUpsertRequest;
import com.communitysport.sysconfig.entity.SysConfig;
import com.communitysport.sysconfig.mapper.SysConfigMapper;

@Service
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    public SysConfigService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    public List<SysConfigItem> list(String keyword) {
        LambdaQueryWrapper<SysConfig> qw = new LambdaQueryWrapper<SysConfig>();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            qw.and(w -> w.like(SysConfig::getCfgKey, k).or().like(SysConfig::getRemark, k));
        }
        qw.orderByAsc(SysConfig::getCfgKey);

        List<SysConfig> rows = sysConfigMapper.selectList(qw);
        List<SysConfigItem> items = new ArrayList<>();
        if (rows != null) {
            for (SysConfig r : rows) {
                items.add(toItem(r));
            }
        }
        return items;
    }

    @Transactional
    public SysConfigItem upsert(SysConfigUpsertRequest request) {
        if (request == null || !StringUtils.hasText(request.getCfgKey()) || request.getCfgValue() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cfgKey/cfgValue required");
        }

        String key = request.getCfgKey().trim();
        if (!StringUtils.hasText(key) || key.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cfgKey length must be 1-100");
        }

        String val = request.getCfgValue().trim();
        if (val.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cfgValue length must be <=255");
        }

        String remark = null;
        if (request.getRemark() != null) {
            remark = StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null;
            if (remark != null && remark.length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remark length must be <=255");
            }
        }

        SysConfig existing = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getCfgKey, key));
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            SysConfig row = new SysConfig();
            row.setCfgKey(key);
            row.setCfgValue(val);
            row.setRemark(remark);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            sysConfigMapper.insert(row);
            return toItem(row);
        }

        boolean changed = false;
        if (!Objects.equals(existing.getCfgValue(), val)) {
            existing.setCfgValue(val);
            changed = true;
        }
        if (!Objects.equals(existing.getRemark(), remark)) {
            existing.setRemark(remark);
            changed = true;
        }
        if (changed) {
            existing.setUpdatedAt(now);
            sysConfigMapper.updateById(existing);
        }
        return toItem(existing);
    }

    private SysConfigItem toItem(SysConfig row) {
        if (row == null) {
            return null;
        }
        SysConfigItem item = new SysConfigItem();
        item.setId(row.getId());
        item.setCfgKey(row.getCfgKey());
        item.setCfgValue(row.getCfgValue());
        item.setRemark(row.getRemark());
        item.setCreatedAt(row.getCreatedAt());
        item.setUpdatedAt(row.getUpdatedAt());
        return item;
    }
}
