package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.CategoryDTO;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.entity.Category;
import com.example.personal_blog.mapper.ArticleMapper;
import com.example.personal_blog.mapper.CategoryMapper;
import com.example.personal_blog.service.CategoryService;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<Category> list() {
        return categoryMapper.listWithCounts();
    }

    @Override
    public Category create(CategoryDTO dto) {
        Category category = new Category();
        category.setUserId(UserContext.requireUserId());
        category.setName(dto.name().trim());
        category.setDescription(dto.description() == null ? "" : dto.description().trim());
        category.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, "分类名称已存在");
        }
        category.setArticleCount(0L);
        return category;
    }

    @Override
    @Transactional
    public Category update(Long id, CategoryDTO dto) {
        Category category = owned(id);
        category.setName(dto.name().trim());
        category.setDescription(dto.description() == null ? "" : dto.description().trim());
        try {
            categoryMapper.updateById(category);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, "分类名称已存在");
        }
        category.setArticleCount(articleMapper.selectCount(new QueryWrapper<Article>().eq("category_id", id)));
        return category;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        owned(id);
        if (articleMapper.selectCount(new QueryWrapper<Article>().eq("category_id", id)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "分类下仍有文章，请先移动文章");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public void lockForArticle(Long id) {
        if (id != null && categoryMapper.lockById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
    }

    private Category owned(Long id) {
        Category category = categoryMapper.lockById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        if (!category.getUserId().equals(UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能维护自己创建的分类");
        }
        return category;
    }
}
