package com.example.pvplatform.module.user.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.user.dto.UpdateUserRolesRequest;
import com.example.pvplatform.module.user.dto.UpdateUserStatusRequest;
import com.example.pvplatform.module.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public Result<?> listUsers(
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String role) {
        return Result.success(adminUserService.listUsers(pageNum, pageSize, keyword, status, role));
    }

    @PutMapping("/users/{userId}/status")
    public Result<?> updateUserStatus(@PathVariable Long userId,
                                      @Valid @RequestBody UpdateUserStatusRequest request) {
        adminUserService.updateUserStatus(userId, request.status());
        return Result.success();
    }

    @PutMapping("/users/{userId}/roles")
    public Result<?> updateUserRoles(@PathVariable Long userId,
                                     @Valid @RequestBody UpdateUserRolesRequest request) {
        adminUserService.updateUserRoles(userId, request);
        return Result.success();
    }

    @DeleteMapping("/users/{userId}")
    public Result<?> deleteUser(@PathVariable Long userId) {
        adminUserService.deleteUser(userId);
        return Result.success();
    }
}
