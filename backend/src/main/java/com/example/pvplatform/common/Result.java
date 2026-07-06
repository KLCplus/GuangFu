package com.example.pvplatform.common;

public record Result<T>(int code, String message, T data) {
    public static Result<Void> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static Result<Void> fail(String message) {
        return fail(500, message);
    }

    public static Result<Void> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
