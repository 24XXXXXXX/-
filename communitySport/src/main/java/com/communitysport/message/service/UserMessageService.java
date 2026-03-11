package com.communitysport.message.service;

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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.communitysport.message.dto.UnreadCountResponse;
import com.communitysport.message.dto.UserMessageItem;
import com.communitysport.message.dto.UserMessagePageResponse;
import com.communitysport.message.entity.UserMessage;
import com.communitysport.message.mapper.UserMessageMapper;
import com.communitysport.security.AuthenticatedUser;

@Service
public class UserMessageService {

    private final UserMessageMapper userMessageMapper;

    public UserMessageService(UserMessageMapper userMessageMapper) {
        this.userMessageMapper = userMessageMapper;
    }

    public UserMessagePageResponse myMessages(AuthenticatedUser principal, Integer page, Integer size, Integer readFlag) {
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

        Integer rf = null;
        if (readFlag != null) {
            if (readFlag != 0 && readFlag != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "readFlag invalid");
            }
            rf = readFlag;
        }

        LambdaQueryWrapper<UserMessage> countQw = new LambdaQueryWrapper<UserMessage>()
            .eq(UserMessage::getUserId, userId);
        if (rf != null) {
            countQw.eq(UserMessage::getReadFlag, rf);
        }
        long total = userMessageMapper.selectCount(countQw);

        long offset = (long) (p - 1) * s;
        List<UserMessageItem> items = new ArrayList<>();
        if (offset < total) {
            LambdaQueryWrapper<UserMessage> listQw = new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId);
            if (rf != null) {
                listQw.eq(UserMessage::getReadFlag, rf);
            }
            listQw.orderByDesc(UserMessage::getId).last("LIMIT " + s + " OFFSET " + offset);

            List<UserMessage> rows = userMessageMapper.selectList(listQw);
            if (rows != null) {
                for (UserMessage r : rows) {
                    items.add(toItem(r));
                }
            }
        }

        UserMessagePageResponse resp = new UserMessagePageResponse();
        resp.setPage(p);
        resp.setSize(s);
        resp.setTotal(total);
        resp.setItems(items);
        return resp;
    }

    public UnreadCountResponse unreadCount(AuthenticatedUser principal) {
        Long userId = requireUserId(principal);
        long cnt = userMessageMapper.selectCount(new LambdaQueryWrapper<UserMessage>()
            .eq(UserMessage::getUserId, userId)
            .eq(UserMessage::getReadFlag, 0));
        UnreadCountResponse resp = new UnreadCountResponse();
        resp.setCount(cnt);
        return resp;
    }

    @Transactional
    public void markRead(AuthenticatedUser principal, Long id) {
        Long userId = requireUserId(principal);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
        }

        UserMessage row = userMessageMapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!Objects.equals(row.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (row.getReadFlag() != null && row.getReadFlag() == 1) {
            return;
        }

        userMessageMapper.update(null, new LambdaUpdateWrapper<UserMessage>()
            .set(UserMessage::getReadFlag, 1)
            .set(UserMessage::getReadAt, LocalDateTime.now())
            .eq(UserMessage::getId, id)
            .eq(UserMessage::getUserId, userId));
    }

    @Transactional
    public void markAllRead(AuthenticatedUser principal) {
        Long userId = requireUserId(principal);
        userMessageMapper.update(null, new LambdaUpdateWrapper<UserMessage>()
            .set(UserMessage::getReadFlag, 1)
            .set(UserMessage::getReadAt, LocalDateTime.now())
            .eq(UserMessage::getUserId, userId)
            .eq(UserMessage::getReadFlag, 0));
    }

    @Transactional
    public void createMessage(Long userId, String msgType, String title, String content, String refType, Long refId) {
        if (userId == null) {
            return;
        }
        if (!StringUtils.hasText(msgType) || !StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            return;
        }

        UserMessage row = new UserMessage();
        row.setUserId(userId);
        row.setMsgType(msgType.trim());
        row.setTitle(title.trim());
        row.setContent(content.trim());
        row.setRefType(StringUtils.hasText(refType) ? refType.trim() : null);
        row.setRefId(refId);
        row.setReadFlag(0);
        row.setReadAt(null);
        row.setCreatedAt(LocalDateTime.now());
        userMessageMapper.insert(row);
    }

    private UserMessageItem toItem(UserMessage row) {
        UserMessageItem it = new UserMessageItem();
        it.setId(row.getId());
        it.setMsgType(row.getMsgType());
        it.setTitle(row.getTitle());
        it.setContent(row.getContent());
        it.setRefType(row.getRefType());
        it.setRefId(row.getRefId());
        it.setReadFlag(row.getReadFlag());
        it.setReadAt(row.getReadAt());
        it.setCreatedAt(row.getCreatedAt());
        return it;
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal.userId();
    }
}
