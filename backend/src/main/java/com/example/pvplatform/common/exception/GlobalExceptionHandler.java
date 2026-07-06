package com.example.pvplatform.common.exception;

import com.example.pvplatform.common.Result;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException exception) {
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public Result<Void> handleBadRequest(Exception exception) {
        return Result.fail(400, "请求参数不合法");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception exception) {
        return Result.fail(500, exception.getMessage());
    }
}
