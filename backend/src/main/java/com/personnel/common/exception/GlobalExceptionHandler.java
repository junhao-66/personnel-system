package com.personnel.common.exception;

import com.personnel.common.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> handleRateLimit(
        RateLimitExceededException exception
    ) {
        return org.springframework.http.ResponseEntity.status(429)
            .header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
            .header("Cache-Control", "no-store")
            .body(new Result<>(429, exception.getMessage(), null));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleValidationException(BindException exception) {
        var error = exception.getBindingResult().getFieldError();
        return new Result<>(
            400,
            error == null ? "请求参数不合法" : error.getField() + ": " + error.getDefaultMessage(),
            null
        );
    }

    @ExceptionHandler(
        { HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class }
    )
    public Result<Void> handleInvalidRequest(Exception exception) {
        return new Result<>(400, "请求参数格式不正确", null);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return new Result<>(400, exception.getMessage(), null);
    }
}
