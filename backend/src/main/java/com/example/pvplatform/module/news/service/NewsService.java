package com.example.pvplatform.module.news.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.config.OssProperties;
import com.example.pvplatform.infrastructure.oss.OssObjectKeyGenerator;
import com.example.pvplatform.infrastructure.oss.OssStorageService;
import com.example.pvplatform.module.file.service.ImageUploadValidator;
import com.example.pvplatform.module.news.dto.NewsRequest;
import com.example.pvplatform.module.news.vo.NewsVO;
import com.example.pvplatform.persistence.entity.NewsDO;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.entity.SysUserRoleDO;
import com.example.pvplatform.persistence.entity.FileResourceDO;
import com.example.pvplatform.persistence.mapper.FileResourceMapper;
import com.example.pvplatform.persistence.mapper.NewsMapper;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NewsService {
    private static final Set<String> TYPES = Set.of("NEWS", "NOTICE", "MODEL_UPDATE", "ALERT", "SYSTEM_NOTICE", "INDUSTRY_NEWS");
    private static final Set<String> TARGETS = Set.of("ALL", "USER", "ADMIN", "API_USER");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");

    private final NewsMapper newsMapper;
    private final NotificationService notificationService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final FileResourceMapper fileMapper;
    private final OssStorageService oss;
    private final OssObjectKeyGenerator keys;
    private final OssProperties ossProperties;

    public NewsService(NewsMapper newsMapper, NotificationService notificationService,
                       SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper, FileResourceMapper fileMapper,
                       OssStorageService oss, OssObjectKeyGenerator keys, OssProperties ossProperties) {
        this.newsMapper = newsMapper;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.fileMapper = fileMapper; this.oss = oss; this.keys = keys; this.ossProperties = ossProperties;
    }

    @Cacheable(cacheNames = "news:public-list",
        key = "#pageNum + ':' + #pageSize + ':' + (#type == null ? 'all' : #type) + ':' + (#keyword == null ? '' : #keyword.trim()) + ':' + T(java.lang.String).join(',', #root.target.currentRoles())")
    public PageResult<NewsVO> list(int pageNum, int pageSize, String type, String keyword) {
        validatePage(pageNum, pageSize);
        validateOptional(type, TYPES, "新闻类型不合法");
        List<String> roles = currentRoles();
        var query = Wrappers.<NewsDO>lambdaQuery()
            .eq(NewsDO::getStatus, "PUBLISHED")
            .le(NewsDO::getPublishedAt, LocalDateTime.now())
            .eq(type != null && !type.isBlank(), NewsDO::getNewsType, type)
            .and(keyword != null && !keyword.isBlank(), q -> q
                .like(NewsDO::getTitle, keyword.trim())
                .or().like(NewsDO::getSummary, keyword.trim())
                .or().like(NewsDO::getContent, keyword.trim()))
            .and(q -> {
                q.eq(NewsDO::getTargetRole, "ALL");
                roles.forEach(role -> q.or().eq(NewsDO::getTargetRole, role));
            })
            .orderByDesc(NewsDO::getPublishedAt);
        Page<NewsDO> page = newsMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return pageResult(page, pageNum, pageSize);
    }

    /** 保持既有内部调用兼容；公开接口可额外传 keyword。 */
    public PageResult<NewsVO> list(int pageNum, int pageSize, String type) {
        return list(pageNum, pageSize, type, null);
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

    @Cacheable(cacheNames = "news:detail",
        key = "#newsId + ':' + T(java.lang.String).join(',', #root.target.currentRoles())")
    public NewsVO detail(Long newsId) {
        NewsDO news = newsMapper.selectById(newsId);
        if (news == null || !"PUBLISHED".equals(news.getStatus()) || news.getPublishedAt() == null || news.getPublishedAt().isAfter(LocalDateTime.now()) || !canView(news.getTargetRole())) {
            throw new BusinessException(404, "新闻不存在");
        }
        return toVO(news);
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
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

    public NewsVO adminDetail(Long newsId) { return toVO(requireNews(newsId)); }

    @Transactional
    public java.util.Map<String, Object> uploadImage(Long newsId, MultipartFile multipart, boolean cover) {
        NewsDO article = requireNews(newsId);
        var image = ImageUploadValidator.validate(multipart, (cover ? Math.min(5, ossProperties.newsImageMaxSizeMb()) : ossProperties.newsImageMaxSizeMb()) * 1024L * 1024L);
        Long owner = SecurityUtils.requireCurrentUserId();
        String key = cover ? keys.newsCover(newsId, image.extension()) : keys.newsContent(newsId, image.extension());
        try {
            oss.upload(new ByteArrayInputStream(image.bytes()), key, image.contentType(), image.bytes().length);
            FileResourceDO f = new FileResourceDO();
            f.setOwnerUserId(owner); f.setOriginalName(image.originalName()); f.setStorageName(key.substring(key.lastIndexOf('/') + 1)); f.setStoragePath(key); f.setObjectKey(key); f.setContentType(image.contentType()); f.setFileType(image.extension()); f.setBusinessType(cover ? "NEWS_COVER" : "NEWS_CONTENT"); f.setBizId(newsId); f.setFileStatus("BOUND"); f.setFileSize((long) image.bytes().length); f.setChecksum(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.bytes()))); f.setCreatedAt(LocalDateTime.now()); f.setUpdatedAt(LocalDateTime.now()); fileMapper.insert(f);
            String url = "/api/files/" + f.getFileId() + "/view";
            if (cover) { article.setCoverFileId(f.getFileId()); article.setCoverUrl(url); article.setUpdatedAt(LocalDateTime.now()); newsMapper.updateById(article); }
            return java.util.Map.of("fileId", f.getFileId(), "url", url, "coverUrl", url);
        } catch (RuntimeException e) { try { oss.delete(key); } catch (Exception ignored) {} throw e;
        } catch (Exception e) { try { oss.delete(key); } catch (Exception ignored) {} throw new BusinessException(500, "文件保存失败"); }
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public void update(Long newsId, NewsRequest request) {
        validateRequest(request);
        requireNews(newsId);
        NewsDO news = fromRequest(request);
        news.setNewsId(newsId);
        news.setUpdatedAt(LocalDateTime.now());
        newsMapper.updateById(news);
    }

    @Transactional
    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public void publish(Long newsId) {
        NewsDO news = requireNews(newsId);
        if (!"DRAFT".equals(news.getStatus()) && !"OFFLINE".equals(news.getStatus())) {
            throw new BusinessException(400, "新闻状态不允许该操作");
        }
        if (news.getTitle() == null || news.getTitle().isBlank() || news.getContent() == null || Jsoup.parse(news.getContent()).text().isBlank()) throw new BusinessException(400, "发布前请填写标题和正文");
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

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public void offline(Long newsId) {
        NewsDO news = requireNews(newsId);
        if (!"PUBLISHED".equals(news.getStatus())) {
            throw new BusinessException(400, "新闻状态不允许该操作");
        }
        news.setStatus("OFFLINE");
        news.setUpdatedAt(LocalDateTime.now());
        newsMapper.updateById(news);
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
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

    public List<String> currentRoles() {
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
        news.setContent(sanitizeHtml(request.content()));
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

    private String sanitizeHtml(String content) {
        if (content == null || content.isBlank()) return "";
        Safelist safelist = Safelist.relaxed().addTags("img").addAttributes("img", "src", "alt", "title").addProtocols("img", "src", "http", "https");
        return Jsoup.clean(content, safelist);
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
