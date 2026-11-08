package com.example.personal_blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.ArticleDTO;
import com.example.personal_blog.dto.LikeStatus;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/article", "/api/article"})
@RequiredArgsConstructor
@Validated
public class ArticleController {
    private final ArticleService articleService;

    @PostMapping("/publish")
    public Result<Article> publish(@RequestBody @Valid ArticleDTO dto) {
        return Result.success(articleService.publish(dto));
    }

    @PutMapping("/{id}")
    public Result<Article> update(@PathVariable @Positive Long id, @RequestBody @Valid ArticleDTO dto) {
        return Result.success(articleService.update(id, dto));
    }

    @PublicEndpoint
    @GetMapping("/list")
    public Result<Page<Article>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Positive Long authorId,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return Result.success(articleService.list(page, size, categoryId, authorId, keyword));
    }

    @GetMapping("/subscriptions")
    public Result<Page<Article>> subscriptions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Positive Long authorId,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return Result.success(articleService.subscriptions(page, size, categoryId, authorId, keyword));
    }

    @PublicEndpoint
    @GetMapping("/detail/{id}")
    public Result<Article> detail(@PathVariable @Positive Long id) {
        return Result.success(articleService.detail(id));
    }

    @PublicEndpoint
    @GetMapping("/ranking")
    public Result<List<Article>> ranking(
            @RequestParam(defaultValue = "views") String sort,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Positive Long categoryId) {
        return Result.success(articleService.ranking(sort, limit, categoryId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        articleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/like")
    public Result<LikeStatus> like(@PathVariable @Positive Long id) {
        return Result.success(articleService.like(id, true));
    }

    @DeleteMapping("/{id}/like")
    public Result<LikeStatus> unlike(@PathVariable @Positive Long id) {
        return Result.success(articleService.like(id, false));
    }
}
