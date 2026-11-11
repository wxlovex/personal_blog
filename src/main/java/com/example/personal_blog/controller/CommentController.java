package com.example.personal_blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.CommentDto;
import com.example.personal_blog.entity.Comment;
import com.example.personal_blog.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/comment", "/api/comment"})
@RequiredArgsConstructor
@Validated
public class CommentController {
    private final CommentService commentService;

    @PostMapping("/publish")
    public Result<Comment> publish(@RequestBody @Valid CommentDto dto) {
        return Result.success(commentService.publish(dto));
    }

    @GetMapping("/{articleId}")
    public Result<Page<Comment>> list(@PathVariable @Positive Long articleId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(commentService.list(articleId, page, size));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        commentService.delete(id);
        return Result.success();
    }
}
