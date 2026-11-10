package com.example.personal_blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.PlazaArticle;
import com.example.personal_blog.service.ArticleService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/plaza", "/api/plaza"})
@RequiredArgsConstructor
@Validated
public class PlazaController {
    private final ArticleService articleService;

    @PublicEndpoint(ignoreToken = true)
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(ArticleService.PLAZA_CATEGORIES);
    }

    @PublicEndpoint(ignoreToken = true)
    @GetMapping("/articles")
    public Result<Page<PlazaArticle>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Pattern(regexp = "技术|日常|读书|娱乐") String category,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return Result.success(articleService.plaza(page, size, category, keyword));
    }

    @PublicEndpoint(ignoreToken = true)
    @GetMapping("/articles/{id}")
    public Result<PlazaArticle> detail(@PathVariable @Positive Long id) {
        return Result.success(articleService.plazaDetail(id));
    }
}
