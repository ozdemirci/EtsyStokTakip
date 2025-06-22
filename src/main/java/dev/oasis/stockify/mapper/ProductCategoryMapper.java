package dev.oasis.stockify.mapper;

import dev.oasis.stockify.dto.ProductCategoryCreateDTO;
import dev.oasis.stockify.dto.ProductCategoryResponseDTO;
import dev.oasis.stockify.model.ProductCategory;
import org.springframework.stereotype.Component;

/**
 * Mapper for ProductCategory entities and DTOs
 */
@Component
public class ProductCategoryMapper {
    
    /**
     * Convert entity to response DTO
     */
    public ProductCategoryResponseDTO toResponseDTO(ProductCategory category) {
        if (category == null) {
            return null;
        }
          ProductCategoryResponseDTO dto = new ProductCategoryResponseDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setActive(category.getIsActive());
        dto.setSortOrder(category.getSortOrder());
        dto.setHexColor(category.getHexColor());
        dto.setCreatedAt(category.getCreatedAt());
        dto.setUpdatedAt(category.getUpdatedAt());
        // productCount will be set by service layer
        
        return dto;
    }
    
    /**
     * Convert create DTO to entity
     */
    public ProductCategory toEntity(ProductCategoryCreateDTO dto) {
        if (dto == null) {
            return null;
        }
          ProductCategory category = new ProductCategory();
        category.setId(dto.getId());
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setIsActive(dto.getActive());
        category.setSortOrder(dto.getSortOrder());
        category.setHexColor(dto.getHexColor());
        
        return category;
    }
    
    /**
     * Convert entity to create DTO (for editing)
     */
    public ProductCategoryCreateDTO toCreateDTO(ProductCategory category) {
        if (category == null) {
            return null;
        }
          ProductCategoryCreateDTO dto = new ProductCategoryCreateDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setActive(category.getIsActive());
        dto.setSortOrder(category.getSortOrder());
        dto.setHexColor(category.getHexColor());
        
        return dto;
    }
    
    /**
     * Update entity from DTO
     */
    public void updateEntity(ProductCategory category, ProductCategoryCreateDTO dto) {
        if (category == null || dto == null) {
            return;
        }
          category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setIsActive(dto.getActive());
        category.setSortOrder(dto.getSortOrder());
        category.setHexColor(dto.getHexColor());
    }
}
