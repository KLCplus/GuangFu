package com.example.pvplatform.module.user.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public Result<?> profile() {
        return Result.success(userService.profile());
    }
}
