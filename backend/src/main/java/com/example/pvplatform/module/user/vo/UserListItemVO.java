package com.example.pvplatform.module.user.vo;

import java.util.List;

public record UserListItemVO(
    Long userId,
    String username,
    String nickname,
    String email,
    String phone,
    Integer status,
    List<String> roles,
    String createdAt
) {}
