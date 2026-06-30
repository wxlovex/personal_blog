package com.example.personal_blog.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200,"success"),

    FAIL(500,"服务器异常"),

    UNAUTHORIZED(401,"未登录"),

    FORBIDDEN(403,"无权限"),

    NOT_FOUND(404,"资源不存在");

    private final Integer code;
    private final String message;

    ResultCode(Integer code,String message){
        this.code=code;
        this.message=message;
    }

}