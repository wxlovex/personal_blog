package com.example.personal_blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.dto.CommentDto;
import com.example.personal_blog.entity.Comment;

public interface CommentService {
    Comment publish(CommentDto dto);
    Page<Comment> list(Long articleId, int page, int size);
    void delete(Long id);
}
