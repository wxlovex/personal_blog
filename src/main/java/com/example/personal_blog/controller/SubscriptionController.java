package com.example.personal_blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.SubscribedAuthor;
import com.example.personal_blog.dto.SubscriptionStatus;
import com.example.personal_blog.service.SubscriptionService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/user", "/api/user"})
@RequiredArgsConstructor
@Validated
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @PutMapping("/{authorId}/subscription")
    public Result<SubscriptionStatus> subscribe(@PathVariable @Positive Long authorId) {
        return Result.success(subscriptionService.change(authorId, true));
    }

    @DeleteMapping("/{authorId}/subscription")
    public Result<SubscriptionStatus> unsubscribe(@PathVariable @Positive Long authorId) {
        return Result.success(subscriptionService.change(authorId, false));
    }

    @PublicEndpoint
    @GetMapping("/{authorId}/subscription")
    public Result<SubscriptionStatus> status(@PathVariable @Positive Long authorId) {
        return Result.success(subscriptionService.status(authorId));
    }

    @GetMapping("/subscriptions")
    public Result<Page<SubscribedAuthor>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(subscriptionService.list(page, size));
    }
}
