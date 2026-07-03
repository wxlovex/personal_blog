package com.example.personal_blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.dto.ArticleDTO;
import com.example.personal_blog.entity.Article;

import java.util.List;

public interface ArticleService {
    void publish(ArticleDTO articleDTO);

    Page<Article> list(int page, int size);

    Article detail(Long id);

    void delete(Long id);
}
