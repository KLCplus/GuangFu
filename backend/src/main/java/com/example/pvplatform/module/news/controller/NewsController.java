package com.example.pvplatform.module.news.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.news.dto.NewsRequest;
import com.example.pvplatform.module.news.service.NewsService;
import com.example.pvplatform.module.news.service.ExternalNewsSyncService;
import com.example.pvplatform.module.news.service.WeatherWarningSyncService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class NewsController {
    private final NewsService newsService;
    private final ExternalNewsSyncService syncService;
    private final WeatherWarningSyncService weatherSyncService;

    public NewsController(NewsService newsService, ExternalNewsSyncService syncService, WeatherWarningSyncService weatherSyncService) {
        this.newsService = newsService;
        this.syncService = syncService;
        this.weatherSyncService = weatherSyncService;
    }

    @GetMapping("/api/news")
    public Result<?> list(@RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "10") int pageSize,
                          @RequestParam(required = false) String type,
                          @RequestParam(required = false) String keyword) {
        return Result.success(newsService.list(pageNum, pageSize, type, keyword));
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

    @GetMapping("/api/admin/news/{newsId}")
    public Result<?> adminDetail(@PathVariable Long newsId) { return Result.success(newsService.adminDetail(newsId)); }

    @PostMapping("/api/admin/news/{newsId}/cover")
    public Result<?> uploadCover(@PathVariable Long newsId, @RequestParam("file") MultipartFile file) {
        return Result.success(newsService.uploadImage(newsId, file, true));
    }

    @PostMapping("/api/admin/news/{newsId}/images")
    public Result<?> uploadContentImage(@PathVariable Long newsId, @RequestParam("file") MultipartFile file) {
        return Result.success(newsService.uploadImage(newsId, file, false));
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
    @PostMapping("/api/admin/news/{newsId}/publish")
    public Result<?> publishPost(@PathVariable Long newsId) { newsService.publish(newsId); return Result.success(); }

    @PutMapping("/api/admin/news/{newsId}/offline")
    public Result<?> offline(@PathVariable Long newsId) {
        newsService.offline(newsId);
        return Result.success();
    }
    @PostMapping("/api/admin/news/{newsId}/offline")
    public Result<?> offlinePost(@PathVariable Long newsId) { newsService.offline(newsId); return Result.success(); }

    @DeleteMapping("/api/admin/news/{newsId}")
    public Result<?> delete(@PathVariable Long newsId) {
        newsService.delete(newsId);
        return Result.success();
    }

    @PostMapping("/api/admin/news/sync")
    public Result<?> sync(@RequestParam(required = false) String source) {
        if (source != null && "QWEATHER".equalsIgnoreCase(source)) {
            return Result.success(Map.of("QWEATHER", weatherSyncService.sync()));
        }
        return Result.success(source == null || source.isBlank()
            ? syncService.syncConfiguredSources() : Map.of(source.toUpperCase(), syncService.syncSource(source)));
    }
}
