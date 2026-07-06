package com.example.pvplatform.common;

import java.util.List;

public record PageResult<T>(long total, int pageNum, int pageSize, List<T> records) {
    public static <T> PageResult<T> of(List<T> records) {
        return new PageResult<>(records.size(), 1, Math.max(records.size(), 10), records);
    }
}
