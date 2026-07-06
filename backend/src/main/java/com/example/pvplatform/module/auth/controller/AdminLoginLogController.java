package com.example.pvplatform.module.auth.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.auth.service.LoginLogService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminLoginLogController {
    private final LoginLogService loginLogService;

    public AdminLoginLogController(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    @GetMapping("/login-logs")
    public Result<?> listLoginLogs(
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize,
        @RequestParam(required = false) String username,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String loginType) {
        return Result.success(loginLogService.page(pageNum, pageSize, username, status, loginType));
    }
}
