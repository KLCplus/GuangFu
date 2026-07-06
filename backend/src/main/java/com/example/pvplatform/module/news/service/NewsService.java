package com.example.pvplatform.module.news.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.news.dto.NewsRequest;
import com.example.pvplatform.module.news.vo.NewsVO;
import com.example.pvplatform.persistence.entity.NewsDO;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.entity.SysUserRoleDO;
import com.example.pvplatform.persistence.mapper.NewsMapper;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class NewsService {
    private static final Set<String> TYPES = Set.of("NEWS", "NOTICE", "MODEL_UPDATE", "ALERT");
    private static final Set<String> TARGETS = Set.of("ALL", "USER", "ADMIN", "API_USER");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");

    private final NewsMapper newsMapper;
    private final NotificationService notificationService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public NewsService(NewsMapper newsMapper, NotificationService notificationService,
                       SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper) {
        this.newsMapper = newsMapper;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public PageResult<NewsVO> list(int pageNum, int pageSize, String type) {
        validatePage(pageNum, pageSize);
        validateOptional(type, TYPES, "新闻类型不合法");
        List<String> roles = currentRoles();
        var query = Wrappers.<NewsDO>lambdaQuery()
            .eq(NewsDO::getStatus, "PUBLISHED")
            .eq(type != null && !type.isBlank(), NewsDO::getNewsType, type)
            .and(q -> {
                q.eq(NewsDO::getTargetRole, "ALL");
                roles.forEach(role -> q.or().eq(NewsDO::getTargetRole, role));
            })
            .orderByDesc(NewsDO::getPublishedAt);
        Page<NewsDO> page = newsMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return pageResult(page, pageNum, pageSize);
    }

    public PageResult<NewsVO> adminList(int pageNum, int pageSize, String status, String type) {
        validatePage(pageNum, pageSize);
        validateOptional(status, STATUSES, "新闻状态不合法");
        validateOptional(type, TYPES, "新闻类型不合法");
        var query = Wrappers.<NewsDO>lambdaQuery()
            .eq(status != null && !status.isBlank(), NewsDO::getStatus, status)
            .eq(type != null && !type.isBlank(), NewsDO::getNewsType, type)
            .orderByDesc(NewsDO::getCreatedAt);
        Page<NewsDO> page = newsMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return pageResult(page, pageNum, pageSize);
    }

    public NewsVO detail(Long newsId) {
        NewsDO news = newsMapper.selectById(newsId);
        if (news == null || !"PUBLISHED".equals(news.getStatus()) || !canView(news.getTargetRole())) {
            throw new BusinessException(404, "新闻不存在");
        }
        return toVO(news);
    }

    public Long create(NewsRequest request) {
        validateRequest(request);
        NewsDO news = fromRequest(request);
        news.setStatus("DRAFT");
        news.setPublisherId(SecurityUtils.requireCurrentUserId());
        news.setCreatedAt(LocalDateTime.now());
        news.setUpdatedAt(LocalDateTime.now());
        news.setDeleted(0);
        newsMapper.insert(news);
        return news.getNewsId();
    }

    public void update(Long newsId, NewsRequest request) {
        validateRequest(request);
        requireNews(newsId);
        NewsDO news = fromRequest(request);
        news.setNewsId(newsId);
        news.setUpdatedAt(LocalDateTime.now());
        newsMapper.updateById(news);
    }

    @Transactional
    public void publish(Long newsId) {
        NewsDO news = requireNews(newsId);
        if (!"DRAFT".equals(news.getStatus()) && !"OFFLINE".equals(news.getStatus())) {
            throw new BusinessException(400, "新闻状态不允许该操作");
        }
        news.setStatus("PUBLISHED");
        news.setPublisherId(SecurityUtils.requireCurrentUserId());
        news.setPublishedAt(LocalDateTime.now());
        news.setUpdatedAt(LocalDateTime.now());
        newsMapper.updateById(news);
        if (Set.of("NOTICE", "ALERT", "MODEL_UPDATE").contains(news.getNewsType())) {
            notificationService.createForUsers(targetUserIds(news.getTargetRole()), news.getTitle(),
                news.getSummary() == null ? news.getContent() : news.getSummary(),
                news.getNewsType(), news.getNewsId());
        }
    }

    public void offline(Long newsId) {
        NewsDO news = requireNews(newsId);
        if (!"PUBLISHED".equals(news.getStatus())) {
            throw new BusinessException(400, "新闻状态不允许该操作");
        }
        news.setStatus("OFFLINE");
        news.setUpdatedAt(LocalDateTime.now());
        newsMapper.updateById(news);
    }

    public void delete(Long newsId) {
        if (newsMapper.deleteById(newsId) == 0) {
            throw new BusinessException(404, "新闻不存在");
        }
    }

    private List<Long> targetUserIds(String targetRole) {
        List<SysUserDO> activeUsers = userMapper.selectList(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getStatus, 1));
        if ("ALL".equals(targetRole)) {
            return activeUsers.stream().map(SysUserDO::getUserId).toList();
        }
        SysRoleDO role = roleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
            .eq(SysRoleDO::getRoleCode, targetRole).eq(SysRoleDO::getStatus, 1).last("LIMIT 1"));
        if (role == null) {
            return List.of();
        }
        Set<Long> activeIds = activeUsers.stream().map(SysUserDO::getUserId)
            .collect(java.util.stream.Collectors.toSet());
        return userRoleMapper.selectList(Wrappers.<SysUserRoleDO>lambdaQuery()
                .eq(SysUserRoleDO::getRoleId, role.getRoleId())).stream()
            .map(SysUserRoleDO::getUserId).filter(activeIds::contains).distinct().toList();
    }

    private NewsDO requireNews(Long newsId) {
        NewsDO news = newsMapper.selectById(newsId);
        if (news == null) {
            throw new BusinessException(404, "新闻不存在");
        }
        return news;
    }

    private boolean canView(String targetRole) {
        return "ALL".equals(targetRole) || currentRoles().contains(targetRole);
    }

    private List<String> currentRoles() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .map(value -> value.replaceFirst("^ROLE_", "")).toList();
    }

    private NewsDO fromRequest(NewsRequest request) {
        NewsDO news = new NewsDO();
        news.setTitle(request.title().trim());
        news.setSummary(request.summary());
        news.setContent(request.content());
        news.setCoverUrl(request.coverUrl());
        news.setNewsType(request.newsType());
        news.setTargetRole(request.targetRole());
        return news;
    }

    private void validateRequest(NewsRequest request) {
        if (!TYPES.contains(request.newsType()) || !TARGETS.contains(request.targetRole())) {
            throw new BusinessException(400, "新闻类型或目标角色不合法");
        }
    }

    private void validateOptional(String value, Set<String> allowed, String message) {
        if (value != null && !value.isBlank() && !allowed.contains(value)) {
            throw new BusinessException(400, message);
        }
    }

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
    }

    private PageResult<NewsVO> pageResult(Page<NewsDO> page, int pageNum, int pageSize) {
        return new PageResult<>(page.getTotal(), pageNum, pageSize,
            page.getRecords().stream().map(this::toVO).toList());
    }

    private NewsVO toVO(NewsDO news) {
        return new NewsVO(news.getNewsId(), news.getTitle(), news.getSummary(), news.getContent(),
            news.getCoverUrl(), news.getNewsType(), news.getTargetRole(), news.getStatus(),
            news.getPublisherId(), news.getPublishedAt(), news.getCreatedAt(), news.getUpdatedAt());
    }
}
