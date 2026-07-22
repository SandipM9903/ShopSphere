package com.shopsphere.backend.dto;

import com.shopsphere.backend.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private UUID id;
    private String name;
    private String description;

    // A static factory method that converts an entity into this DTO.
    // Keeps the mapping logic in one obvious place instead of scattering
    // "new CategoryResponse(cat.getId(), cat.getName()...)" across every
    // service method that needs to return one.
    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription());
    }
}