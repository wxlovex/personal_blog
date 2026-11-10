package com.example.personal_blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.dto.ArticleDTO;
import com.example.personal_blog.dto.LikeStatus;
import com.example.personal_blog.dto.PlazaArticle;
import com.example.personal_blog.entity.Article;
import java.util.List;

public interface ArticleService {
    List<String> PLAZA_CATEGORIES = List.of("技术", "日常", "读书", "娱乐");
    Article publish(ArticleDTO dto);
    Article update(Long id, ArticleDTO dto);
    Article updateCategory(Long id, Long categoryId);
    Article updatePublication(Long id, boolean published, String category);
    Page<PlazaArticle> plaza(int page, int size, String category, String keyword);
    PlazaArticle plazaDetail(Long id);
    Page<Article> list(int page, int size, Long categoryId, Long authorId, String keyword);
    Page<Article> subscriptions(int page, int size, Long categoryId, Long authorId, String keyword);
    Article detail(Long id);
    void delete(Long id);
    LikeStatus like(Long id, boolean liked);
    List<Article> ranking(String sort, int limit, Long categoryId);
}
