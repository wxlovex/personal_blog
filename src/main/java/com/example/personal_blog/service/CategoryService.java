package com.example.personal_blog.service;

import com.example.personal_blog.dto.CategoryDTO;
import com.example.personal_blog.entity.Category;
import java.util.List;

public interface CategoryService {
    List<Category> list();
    Category create(CategoryDTO dto);
    Category update(Long id, CategoryDTO dto);
    void delete(Long id);
    void lockForArticle(Long id);
}
