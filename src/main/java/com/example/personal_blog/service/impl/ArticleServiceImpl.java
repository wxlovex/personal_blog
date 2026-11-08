package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.ArticleDTO;
import com.example.personal_blog.dto.LikeStatus;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.entity.ArticleLike;
import com.example.personal_blog.entity.Comment;
import com.example.personal_blog.mapper.ArticleLikeMapper;
import com.example.personal_blog.mapper.ArticleMapper;
import com.example.personal_blog.mapper.CommentMapper;
import com.example.personal_blog.service.ArticleService;
import com.example.personal_blog.service.CategoryService;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {
    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper likeMapper;
    private final CommentMapper commentMapper;
    private final CategoryService categoryService;

    @Override
    @Transactional
    public Article publish(ArticleDTO dto) {
        Long userId = UserContext.requireUserId();
        categoryService.lockForArticle(dto.getCategoryId());
        Article article = new Article();
        article.setTitle(dto.getTitle().trim());
        article.setContent(dto.getContent());
        article.setCategoryId(dto.getCategoryId());
        article.setUserId(userId);
        article.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        article.setUpdateTime(article.getCreateTime());
        article.setViewCount(0L);
        article.setLikeCount(0L);
        articleMapper.insert(article);
        return articleMapper.selectByIdWithAuthor(article.getId(), userId);
    }

    @Override
    @Transactional
    public Article update(Long id, ArticleDTO dto) {
        owned(id);
        categoryService.lockForArticle(dto.getCategoryId());
        // 只写入编辑字段，避免覆盖并发访问或点赞产生的计数。
        Article changes = new Article();
        changes.setId(id);
        changes.setTitle(dto.getTitle().trim());
        changes.setContent(dto.getContent());
        changes.setCategoryId(dto.getCategoryId());
        changes.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
        articleMapper.updateById(changes);
        return articleMapper.selectByIdWithAuthor(id, UserContext.getUserId());
    }

    @Override
    public Page<Article> list(int page, int size, Long categoryId, Long authorId, String keyword) {
        return articleMapper.selectPageWithAuthor(new Page<>(page, size), categoryId, authorId,
                keyword == null ? null : keyword.trim(), UserContext.getUserId(), false);
    }

    @Override
    public Page<Article> subscriptions(int page, int size, Long categoryId, Long authorId, String keyword) {
        return articleMapper.selectPageWithAuthor(new Page<>(page, size), categoryId, authorId,
                keyword == null ? null : keyword.trim(), UserContext.requireUserId(), true);
    }

    @Override
    @Transactional
    public Article detail(Long id) {
        if (articleMapper.incrementViews(id) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return articleMapper.selectByIdWithAuthor(id, UserContext.getUserId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        owned(id);
        commentMapper.delete(new QueryWrapper<Comment>().eq("article_id", id));
        likeMapper.delete(new QueryWrapper<ArticleLike>().eq("article_id", id));
        articleMapper.deleteById(id);
    }

    @Override
    @Transactional
    public LikeStatus like(Long id, boolean liked) {
        Long userId = UserContext.requireUserId();
        // 对同一文章串行处理点赞、取消和删除；唯一约束提供第二层保障。
        Article article = lock(id);
        QueryWrapper<ArticleLike> query = new QueryWrapper<ArticleLike>()
                .eq("article_id", id).eq("user_id", userId);
        boolean exists = likeMapper.selectCount(query) > 0;
        if (liked && !exists) {
            ArticleLike relation = new ArticleLike();
            relation.setArticleId(id);
            relation.setUserId(userId);
            relation.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
            likeMapper.insert(relation);
            articleMapper.changeLikes(id, 1);
            article.setLikeCount(article.getLikeCount() + 1);
        } else if (!liked && exists) {
            likeMapper.delete(query);
            articleMapper.changeLikes(id, -1);
            article.setLikeCount(article.getLikeCount() - 1);
        }
        return new LikeStatus(liked, article.getLikeCount());
    }

    @Override
    public List<Article> ranking(String sort, int limit, Long categoryId) {
        if (!"views".equals(sort) && !"likes".equals(sort)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "sort 只能为 views 或 likes");
        }
        return articleMapper.ranking(sort, limit, categoryId, UserContext.getUserId());
    }

    private Article lock(Long id) {
        Article article = articleMapper.lockById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private Article owned(Long id) {
        Article article = lock(id);
        if (!article.getUserId().equals(UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能维护自己发布的文章");
        }
        return article;
    }
}
