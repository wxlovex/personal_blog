package com.example.personal_blog.common.handler;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {

        log.warn("业务异常：{}", e.getMessage());

        return Result.fail(e.getMessage());
    }

    /**
     * 未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {

        log.error("系统异常", e);

        return Result.fail(e.getMessage());
    }

}