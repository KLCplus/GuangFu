package com.example.pvplatform.module.news.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.news.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "10") int pageSize,
                          @RequestParam(required = false) Integer readStatus) {
        return Result.success(notificationService.list(pageNum, pageSize, readStatus));
    }

    @GetMapping("/unread-count")
    public Result<?> unreadCount() {
        return Result.success(Map.of("unreadCount", notificationService.unreadCount()));
    }

    @PutMapping("/{notificationId}/read")
    public Result<?> markRead(@PathVariable Long notificationId) {
        notificationService.markRead(notificationId);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<?> markAllRead() {
        return Result.success(Map.of("updated", notificationService.markAllRead()));
    }
}
