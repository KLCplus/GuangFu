package com.example.pvplatform.module.news.controller;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.news.dto.NewsRequest;
import com.example.pvplatform.module.news.service.NewsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/api/news")
    public Result<?> list() {
        return Result.success(PageResult.of(newsService.list()));
    }

    @GetMapping("/api/news/{newsId}")
    public Result<?> detail(@PathVariable Long newsId) {
        return Result.success(newsService.detail(newsId));
    }

    @PostMapping("/api/admin/news")
    public Result<?> create(@RequestBody NewsRequest request) {
        return Result.success(Map.of("newsId", 3L, "title", request.title()));
    }

    @PutMapping("/api/admin/news/{newsId}")
    public Result<?> update(@PathVariable Long newsId, @RequestBody NewsRequest request) {
        return Result.success(Map.of("newsId", newsId, "updated", true));
    }

    @DeleteMapping("/api/admin/news/{newsId}")
    public Result<?> delete(@PathVariable Long newsId) {
        return Result.success(Map.of("newsId", newsId, "deleted", true));
    }
}
