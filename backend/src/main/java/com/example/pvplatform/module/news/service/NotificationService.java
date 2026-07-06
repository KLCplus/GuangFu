package com.example.pvplatform.module.news.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.news.vo.NotificationVO;
import com.example.pvplatform.persistence.entity.UserNotificationDO;
import com.example.pvplatform.persistence.mapper.UserNotificationMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    private final UserNotificationMapper notificationMapper;

    public NotificationService(UserNotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public PageResult<NotificationVO> list(int pageNum, int pageSize, Integer readStatus) {
        validatePage(pageNum, pageSize);
        Long userId = SecurityUtils.requireCurrentUserId();
        var query = Wrappers.<UserNotificationDO>lambdaQuery()
            .eq(UserNotificationDO::getUserId, userId)
            .eq(readStatus != null, UserNotificationDO::getReadStatus, readStatus)
            .orderByDesc(UserNotificationDO::getCreatedAt);
        Page<UserNotificationDO> page = notificationMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize,
            page.getRecords().stream().map(this::toVO).toList());
    }

    public long unreadCount() {
        return notificationMapper.selectCount(Wrappers.<UserNotificationDO>lambdaQuery()
            .eq(UserNotificationDO::getUserId, SecurityUtils.requireCurrentUserId())
            .eq(UserNotificationDO::getReadStatus, 0));
    }

    public void markRead(Long notificationId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        UserNotificationDO update = new UserNotificationDO();
        update.setReadStatus(1);
        update.setReadTime(LocalDateTime.now());
        notificationMapper.update(update, Wrappers.<UserNotificationDO>lambdaUpdate()
            .eq(UserNotificationDO::getNotificationId, notificationId)
            .eq(UserNotificationDO::getUserId, userId)
            .eq(UserNotificationDO::getReadStatus, 0));
        if (notificationMapper.selectCount(Wrappers.<UserNotificationDO>lambdaQuery()
            .eq(UserNotificationDO::getNotificationId, notificationId)
            .eq(UserNotificationDO::getUserId, userId)) == 0) {
            throw new BusinessException(404, "通知不存在");
        }
    }

    public int markAllRead() {
        UserNotificationDO update = new UserNotificationDO();
        update.setReadStatus(1);
        update.setReadTime(LocalDateTime.now());
        return notificationMapper.update(update, Wrappers.<UserNotificationDO>lambdaUpdate()
            .eq(UserNotificationDO::getUserId, SecurityUtils.requireCurrentUserId())
            .eq(UserNotificationDO::getReadStatus, 0));
    }

    public void createForUsers(List<Long> userIds, String title, String content,
                               String type, Long relatedId) {
        if (userIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<UserNotificationDO> rows = userIds.stream().distinct().map(userId -> {
            UserNotificationDO row = new UserNotificationDO();
            row.setUserId(userId);
            row.setTitle(title);
            row.setContent(content);
            row.setNotificationType(type);
            row.setRelatedType("NEWS");
            row.setRelatedId(relatedId);
            row.setReadStatus(0);
            row.setCreatedAt(now);
            return row;
        }).toList();
        for (int from = 0; from < rows.size(); from += 500) {
            notificationMapper.batchInsert(rows.subList(from, Math.min(from + 500, rows.size())));
        }
    }

    private NotificationVO toVO(UserNotificationDO row) {
        return new NotificationVO(row.getNotificationId(), row.getTitle(), row.getContent(),
            row.getNotificationType(), row.getRelatedType(), row.getRelatedId(),
            row.getReadStatus(), row.getReadTime(), row.getCreatedAt());
    }

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
    }
}
