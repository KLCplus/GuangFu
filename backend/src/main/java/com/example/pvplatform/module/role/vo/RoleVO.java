package com.example.pvplatform.module.role.vo;

public record RoleVO(
    Long roleId,
    String roleCode,
    String roleName,
    String description,
    Integer status,
    long userCount,
    String createdAt
) {}
