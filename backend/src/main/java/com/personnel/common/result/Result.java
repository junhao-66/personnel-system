package com.personnel.common.result;

public record Result<T>(Integer code, String msg, T data) {
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static Result<Void> success() {
        return new Result<>(200, "success", null);
    }
}
