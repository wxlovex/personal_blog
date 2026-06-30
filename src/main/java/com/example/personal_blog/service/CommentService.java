package com.example.personal_blog.service;

import com.example.personal_blog.dto.CommentDto;
import com.example.personal_blog.entity.Comment;

import java.util.List;

public interface CommentService {
    void publish(CommentDto dto); // 发表评论

    List<Comment> list(Long articleId); // 查询评论
}
