package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.personal_blog.dto.CommentDto;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.entity.Comment;
import com.example.personal_blog.mapper.ArticleMapper;
import com.example.personal_blog.mapper.CommentMapper;
import com.example.personal_blog.service.CommentService;
import com.example.personal_blog.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Override
    public void publish(CommentDto dto) {
        //收到请求
        Article article = articleMapper.selectById(dto.getArticleId());

        //检查文章是否存在
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }

        //从JWT获取当前用户
        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setUserId(UserContext.getUserId());
        comment.setContent(dto.getContent());
        comment.setCreateTime(LocalDateTime.now());

        //保存评论
        int insert = commentMapper.insert(comment);
        if (insert <= 0) {
            throw new RuntimeException("评论保存失败");
        }
    }

    @Override
    public List<Comment> list(Long articleId) {
        return commentMapper.selectList(
                new QueryWrapper<Comment>()
                        .eq("article_id", articleId)
                        .orderByAsc("create_time")
        );
    }
}
