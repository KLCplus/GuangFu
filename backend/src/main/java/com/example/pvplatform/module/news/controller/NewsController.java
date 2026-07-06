package com.example.pvplatform.module.news.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.news.dto.NewsRequest;
import com.example.pvplatform.module.news.service.NewsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/api/news")
    public Result<?> list(@RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "10") int pageSize,
                          @RequestParam(required = false) String type) {
        return Result.success(newsService.list(pageNum, pageSize, type));
    }

    @GetMapping("/api/news/{newsId}")
    public Result<?> detail(@PathVariable Long newsId) {
        return Result.success(newsService.detail(newsId));
    }

    @GetMapping("/api/admin/news")
    public Result<?> adminList(@RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "10") int pageSize,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String type) {
        return Result.success(newsService.adminList(pageNum, pageSize, status, type));
    }

    @PostMapping("/api/admin/news")
    public Result<?> create(@Valid @RequestBody NewsRequest request) {
        return Result.success(Map.of("newsId", newsService.create(request), "title", request.title()));
    }

    @PutMapping("/api/admin/news/{newsId}")
    public Result<?> update(@PathVariable Long newsId, @Valid @RequestBody NewsRequest request) {
        newsService.update(newsId, request);
        return Result.success();
    }

    @PutMapping("/api/admin/news/{newsId}/publish")
    public Result<?> publish(@PathVariable Long newsId) {
        newsService.publish(newsId);
        return Result.success();
    }

    @PutMapping("/api/admin/news/{newsId}/offline")
    public Result<?> offline(@PathVariable Long newsId) {
        newsService.offline(newsId);
        return Result.success();
    }

    @DeleteMapping("/api/admin/news/{newsId}")
    public Result<?> delete(@PathVariable Long newsId) {
        newsService.delete(newsId);
        return Result.success();
    }
}
