package com.example.personal_blog.controller;

import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.Result;
import com.example.personal_blog.common.ResultCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
public class ApiErrorController implements ErrorController {
    @PublicEndpoint(ignoreToken = true)
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<Result<Void>> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer code ? code : 500;
        String message = Arrays.stream(ResultCode.values()).filter(code -> code.getCode() == status)
                .map(ResultCode::getMessage).findFirst().orElse("请求处理失败");
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(new Result<>(status, message, null));
    }
}
