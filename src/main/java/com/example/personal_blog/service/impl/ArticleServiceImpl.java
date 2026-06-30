package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.personal_blog.dto.ArticleDTO;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.mapper.ArticleMapper;
import com.example.personal_blog.service.ArticleService;
import com.example.personal_blog.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ArticleServiceImpl implements ArticleService {
    @Autowired
    private ArticleMapper articleMapper;

    // 发布文章
    @Override
    public void publish(ArticleDTO articleDTO) {
        Article article = new Article();
        article.setTitle(articleDTO.getTitle());
        article.setContent(articleDTO.getContent());

        // 👇 从JWT上下文获取当前用户
        article.setUserId(UserContext.getUserId());

        article.setCreateTime(LocalDateTime.now());
        article.setViewCount(0);
        article.setLikeCount(0);

        articleMapper.insert(article);
    }

    // 文章分页
    @Override
    public List<Article> list(int page, int size) {
        int offset=(page-1)*size;

        return articleMapper.selectList(
                new QueryWrapper<Article>()
                        .orderByDesc("create_time")
                        .last("limit " + offset + "," + size)
        );
    }

    // 文章详情(浏览量+1)
    @Override
    public Article detail(Long id) {
        Article article = articleMapper.selectById(id);
        if(article==null){
            throw new RuntimeException("the article is not exists");
        }

        article.setViewCount(article.getViewCount()+1);
        articleMapper.updateById(article);
        return article;
    }

    // 删除文章(仅限自己)
    @Override
    public void delete(Long id) {
        Article article = articleMapper.selectById(id);
        if(article==null){
            throw new RuntimeException("the article is not exists");
        }

        Long userId = UserContext.getUserId();
        if(!article.getUserId().equals(userId)){
            throw new RuntimeException("have no permissions to delete");
        }

        articleMapper.deleteById(id);
    }
}
