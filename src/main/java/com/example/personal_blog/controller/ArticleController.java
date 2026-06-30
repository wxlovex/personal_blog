package com.example.personal_blog.controller;

import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.ArticleDTO;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    private ArticleService articleService;

    // 发布文章
    @PostMapping("/publish")
    public String publish(@RequestBody ArticleDTO dto) {
        articleService.publish(dto);
        return "发布成功";
    }

    // 文章列表
    @GetMapping("/list")
    public Result<List<Article>> list(@RequestParam int page,
                                      @RequestParam int size) {
        return Result.success(articleService.list(page, size));
    }

    // 文章详情
    @GetMapping("/detail/{id}")
    public Article detail(@PathVariable Long id) {
        return articleService.detail(id);
    }

    // 删除文章
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        articleService.delete(id);
        return "删除成功";
    }
}
