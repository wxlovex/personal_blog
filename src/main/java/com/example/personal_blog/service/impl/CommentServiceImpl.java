package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.CommentDto;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.entity.Comment;
import com.example.personal_blog.mapper.ArticleMapper;
import com.example.personal_blog.mapper.CommentMapper;
import com.example.personal_blog.service.CommentService;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;

    @Override
    @Transactional
    public Comment publish(CommentDto dto) {
        Long userId = UserContext.requireUserId();
        if (articleMapper.lockById(dto.getArticleId()) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent().trim());
        comment.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        commentMapper.insert(comment);
        return comment;
    }

    @Override
    public Page<Comment> list(Long articleId, int page, int size) {
        if (articleMapper.selectById(articleId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return commentMapper.listWithAuthor(new Page<>(page, size), articleId);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        Article article = articleMapper.lockById(comment.getArticleId());
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        Long userId = UserContext.requireUserId();
        if (!userId.equals(comment.getUserId()) && !userId.equals(article.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己的评论或自己文章下的评论");
        }
        if (commentMapper.deleteById(id) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
    }
}
