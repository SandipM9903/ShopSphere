package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.CategoryRequest;
import com.shopsphere.backend.dto.CategoryResponse;
import com.shopsphere.backend.entity.Category;
import com.shopsphere.backend.repository.CategoryRepository;
import com.shopsphere.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;


import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(UUID id) {
        Category category = findCategoryOrThrow(id);
        return CategoryResponse.fromEntity(category);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        Category saved = categoryRepository.save(category);
        return CategoryResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = findCategoryOrThrow(id);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        // No explicit save() call needed here - see explanation below.
        return CategoryResponse.fromEntity(category);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findCategoryOrThrow(id);
        categoryRepository.delete(category);
    }

    private Category findCategoryOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}