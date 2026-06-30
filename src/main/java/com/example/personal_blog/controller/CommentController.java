package com.example.personal_blog.controller;

import com.example.personal_blog.dto.CommentDto;
import com.example.personal_blog.entity.Comment;
import com.example.personal_blog.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comment")
public class CommentController {
    @Autowired
    private CommentService commentService;

    @PostMapping("/publish")
    public String publish(@RequestBody CommentDto dto){
        commentService.publish(dto);
        return "评论成功";
    }

    @GetMapping("/{articleId}")
    public List<Comment> list(@PathVariable Long articleId){
        return commentService.list(articleId);
    }
}
