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
import com.example.personal_blog.utils.TextContent;
import com.example.personal_blog.security.AccountQuotaService;
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
    private final AccountQuotaService quota;

    @Override
    @Transactional
    public Comment publish(CommentDto dto) {
        quota.lockUser();
        Long userId = UserContext.requireUserId();
        if (articleMapper.lockOwnedById(dto.getArticleId(), userId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setUserId(userId);
        comment.setContent(TextContent.requireVisible(dto.getContent(), "补记内容不能为空"));
        comment.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        quota.check(AccountQuotaService.bytes(comment.getContent()), 0, 1, 0);
        commentMapper.insert(comment);
        return comment;
    }

    @Override
    public Page<Comment> list(Long articleId, int page, int size) {
        Long userId = UserContext.requireUserId();
        if (articleMapper.selectByIdWithAuthor(articleId, userId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return commentMapper.listWithAuthor(new Page<>(page, size), articleId, userId);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        quota.lockUser();
        Long userId = UserContext.requireUserId();
        Comment comment = commentMapper.selectById(id);
        if (comment == null || !userId.equals(comment.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "补记不存在");
        }
        Article article = articleMapper.lockOwnedById(comment.getArticleId(), userId);
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (commentMapper.deleteById(id) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "补记不存在");
        }
    }
}
