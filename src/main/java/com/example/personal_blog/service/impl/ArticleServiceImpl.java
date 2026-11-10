package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.ArticleDTO;
import com.example.personal_blog.dto.LikeStatus;
import com.example.personal_blog.dto.PlazaArticle;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.entity.ArticleLike;
import com.example.personal_blog.entity.Comment;
import com.example.personal_blog.mapper.ArticleLikeMapper;
import com.example.personal_blog.mapper.ArticleMapper;
import com.example.personal_blog.mapper.CommentMapper;
import com.example.personal_blog.service.ArticleService;
import com.example.personal_blog.service.CategoryService;
import com.example.personal_blog.utils.UserContext;
import com.example.personal_blog.security.AccountQuotaService;
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
    private final AccountQuotaService quota;

    @Override
    @Transactional
    public Article publish(ArticleDTO dto) {
        quota.lockUser();
        Long userId = UserContext.requireUserId();
        categoryService.lockForArticle(dto.getCategoryId());
        Article article = new Article();
        article.setTitle(titleFor(dto));
        article.setContent(dto.getContent());
        article.setCategoryId(dto.getCategoryId());
        article.setUserId(userId);
        article.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        article.setUpdateTime(article.getCreateTime());
        article.setViewCount(0L);
        article.setLikeCount(0L);
        article.setPublished(false);
        quota.check(AccountQuotaService.bytes(article.getTitle(), article.getContent()), 1, 0, 0);
        articleMapper.insert(article);
        return articleMapper.selectByIdWithAuthor(article.getId(), userId);
    }

    @Override
    @Transactional
    public Article update(Long id, ArticleDTO dto) {
        quota.lockUser();
        Article previous = owned(id);
        if (Boolean.TRUE.equals(previous.getPublished())) quota.requireVerifiedEmail();
        categoryService.lockForArticle(dto.getCategoryId());
        Article changes = new Article();
        changes.setId(id);
        changes.setTitle(titleFor(dto));
        changes.setContent(dto.getContent());
        changes.setCategoryId(dto.getCategoryId());
        changes.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
        quota.check(AccountQuotaService.bytes(changes.getTitle(), changes.getContent())
                - AccountQuotaService.bytes(previous.getTitle(), previous.getContent()), 0, 0, 0);
        articleMapper.updateById(changes);
        return articleMapper.selectByIdWithAuthor(id, UserContext.requireUserId());
    }

    @Override
    @Transactional
    public Article updateCategory(Long id, Long categoryId) {
        quota.lockUser();
        owned(id);
        categoryService.lockForArticle(categoryId);
        Article changes = new Article();
        changes.setId(id);
        changes.setCategoryId(categoryId);
        changes.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
        articleMapper.updateById(changes);
        return articleMapper.selectByIdWithAuthor(id, UserContext.requireUserId());
    }

    @Override
    @Transactional
    public Article updatePublication(Long id, boolean published, String category) {
        quota.lockUser();
        Article article = owned(id);
        if (published) quota.requireVerifiedEmail();
        if (published && (category == null || !PLAZA_CATEGORIES.contains(category))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择广场分类");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime publishedTime = published
                ? (Boolean.TRUE.equals(article.getPublished()) ? article.getPublishedTime() : now) : null;
        // Update only visibility metadata; preserve private classification, title and body.
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id).eq(Article::getUserId, UserContext.requireUserId())
                .set(Article::getPublished, published)
                .set(Article::getPlazaCategory, published ? category : null)
                .set(Article::getPublishedTime, publishedTime)
                .set(Article::getUpdateTime, now));
        return articleMapper.selectByIdWithAuthor(id, UserContext.requireUserId());
    }

    @Override
    public Page<PlazaArticle> plaza(int page, int size, String category, String keyword) {
        if (category != null && !PLAZA_CATEGORIES.contains(category)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择有效的广场分类");
        }
        return articleMapper.selectPlazaPage(new Page<>(page, size), category, keyword == null ? null : keyword.trim());
    }

    @Override
    public PlazaArticle plazaDetail(Long id) {
        PlazaArticle article = articleMapper.selectPlazaById(id);
        if (article == null) throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在或尚未公开");
        return article;
    }

    @Override
    public Page<Article> list(int page, int size, Long categoryId, Long authorId, String keyword) {
        return articleMapper.selectPageWithAuthor(new Page<>(page, size), categoryId, authorId,
                keyword == null ? null : keyword.trim(), UserContext.requireUserId(), false);
    }

    @Override
    public Article detail(Long id) {
        Article article = articleMapper.selectByIdWithAuthor(id, UserContext.requireUserId());
        if (article == null) throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        return article;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        quota.lockUser();
        owned(id);
        commentMapper.delete(new QueryWrapper<Comment>().eq("article_id", id));
        likeMapper.delete(new QueryWrapper<ArticleLike>().eq("article_id", id));
        articleMapper.deleteById(id);
    }

    // Retain old routes as explicit tombstones; no social data is queried or changed.
    @Override
    public Page<Article> subscriptions(int page, int size, Long categoryId, Long authorId, String keyword) {
        throw retired();
    }

    @Override
    public LikeStatus like(Long id, boolean liked) { throw retired(); }

    @Override
    public List<Article> ranking(String sort, int limit, Long categoryId) { throw retired(); }

    private BusinessException retired() {
        UserContext.requireUserId();
        return new BusinessException(ResultCode.GONE, "私人日记已停用排行、点赞和订阅功能");
    }

    private Article owned(Long id) {
        Article article = articleMapper.lockOwnedById(id, UserContext.requireUserId());
        if (article == null) throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        return article;
    }

    private String titleFor(ArticleDTO dto) {
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) return dto.getTitle().trim();
        String firstLine = dto.getContent().strip().lines().findFirst().orElse("随手记");
        if (firstLine.length() <= 50) return firstLine;
        int end = Character.isHighSurrogate(firstLine.charAt(49)) ? 49 : 50;
        return firstLine.substring(0, end);
    }
}
