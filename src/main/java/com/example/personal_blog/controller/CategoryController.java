package com.example.personal_blog.controller;

import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.CategoryDTO;
import com.example.personal_blog.entity.Category;
import com.example.personal_blog.service.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/category", "/api/category"})
@RequiredArgsConstructor
@Validated
public class CategoryController {
    private final CategoryService service;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(service.list());
    }

    @PostMapping
    public Result<Category> create(@RequestBody @Valid CategoryDTO dto) {
        return Result.success(service.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable @Positive Long id, @RequestBody @Valid CategoryDTO dto) {
        return Result.success(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return Result.success();
    }
}
