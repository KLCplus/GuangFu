package com.example.pvplatform.module.news.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.news.dto.NewsRequest;
import com.example.pvplatform.module.news.entity.News;
import com.example.pvplatform.persistence.entity.NewsDO;
import com.example.pvplatform.persistence.mapper.NewsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NewsService {
    private final NewsMapper newsMapper;

    public NewsService(NewsMapper newsMapper) {
        this.newsMapper = newsMapper;
    }

    public List<News> list() {
        return newsMapper.selectList(Wrappers.<NewsDO>lambdaQuery()
                .eq(NewsDO::getStatus, "PUBLISHED")
                .orderByDesc(NewsDO::getPublishedAt))
            .stream().map(this::toEntity).toList();
    }

    public News detail(Long newsId) {
        NewsDO news = newsMapper.selectById(newsId);
        if (news == null) {
            throw new BusinessException(404, "新闻不存在");
        }
        return toEntity(news);
    }

    public Long create(NewsRequest request) {
        NewsDO news = fromRequest(request);
        news.setStatus("PUBLISHED");
        news.setTargetRole("ALL");
        news.setPublishedAt(LocalDateTime.now());
        newsMapper.insert(news);
        return news.getNewsId();
    }

    public void update(Long newsId, NewsRequest request) {
        NewsDO news = fromRequest(request);
        news.setNewsId(newsId);
        if (newsMapper.updateById(news) == 0) {
            throw new BusinessException(404, "新闻不存在");
        }
    }

    public void delete(Long newsId) {
        if (newsMapper.deleteById(newsId) == 0) {
            throw new BusinessException(404, "新闻不存在");
        }
    }

    private NewsDO fromRequest(NewsRequest request) {
        NewsDO news = new NewsDO();
        news.setTitle(request.title());
        news.setContent(request.content());
        news.setNewsType(request.type());
        return news;
    }

    private News toEntity(NewsDO news) {
        LocalDateTime displayTime = news.getPublishedAt() == null ? news.getCreatedAt() : news.getPublishedAt();
        return new News(news.getNewsId(), news.getTitle(), news.getContent(), news.getNewsType(),
            displayTime == null ? null : displayTime.toString());
    }
}
