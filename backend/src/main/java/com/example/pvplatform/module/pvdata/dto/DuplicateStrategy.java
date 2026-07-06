package com.example.pvplatform.module.pvdata.dto;

import com.example.pvplatform.common.exception.BusinessException;

public enum DuplicateStrategy {
    SKIP, UPDATE, FAIL;

    public static DuplicateStrategy parse(String value) {
        try {
            return value == null ? SKIP : valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "duplicateStrategy 仅支持 SKIP、UPDATE、FAIL");
        }
    }
}
