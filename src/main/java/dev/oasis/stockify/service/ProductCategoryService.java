package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.ProductCategoryCreateDTO;
import dev.oasis.stockify.dto.ProductCategoryResponseDTO;
import dev.oasis.stockify.exception.EntityNotFoundException;
import dev.oasis.stockify.exception.DuplicateEntityException;
import dev.oasis.stockify.mapper.ProductCategoryMapper;
import dev.oasis.stockify.model.ProductCategory;
import dev.oasis.stockify.repository.ProductCategoryRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing product categories
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryMapper categoryMapper;
    private final ServiceTenantUtil serviceTenantUtil;
    
    /**
     * Get all active categories for dropdowns
     */
    public List<ProductCategoryResponseDTO> getAllActiveCategories() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("📂 Fetching all active categories for tenant: {}", currentTenant);
        
        if (currentTenant == null || currentTenant.isEmpty()) {
            log.error("❌ CRITICAL: No tenant context found in getAllActiveCategories()");
            throw new IllegalStateException("Tenant context is required for category operations");
        }
        
        List<ProductCategory> categories = categoryRepository.findByIsActiveTrueOrderBySortOrderAscNameAsc();
        log.debug("📂 Found {} active categories for tenant: {}", categories.size(), currentTenant);
        return categories.stream()
                .map(categoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all categories (including inactive) for admin management
     */
    public List<ProductCategoryResponseDTO> getAllCategories() {
        log.debug("📂 Fetching all categories for admin");
        List<ProductCategory> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        return categories.stream()
                .map(this::enrichWithProductCount)
                .collect(Collectors.toList());
    }
    
    /**
     * Get category by ID
     */
    public Optional<ProductCategoryResponseDTO> getCategoryById(Long id) {
        log.debug("🔍 Fetching category with ID: {}", id);
        return categoryRepository.findById(id)
                .map(this::enrichWithProductCount);
    }
    
    /**
     * Create new category
     */
    @Transactional
    public ProductCategoryResponseDTO createCategory(ProductCategoryCreateDTO createDTO) {
        log.info("➕ Creating new category: {}", createDTO.getName());
        
        // Check if category name already exists
        if (categoryRepository.existsByNameIgnoreCase(createDTO.getName())) {
            throw new DuplicateEntityException("Category with name '" + createDTO.getName() + "' already exists");
        }
        
        ProductCategory category = categoryMapper.toEntity(createDTO);
        ProductCategory savedCategory = categoryRepository.save(category);
        
        log.info("✅ Category created successfully: {}", savedCategory.getName());
        return categoryMapper.toResponseDTO(savedCategory);
    }
    
    /**
     * Update existing category
     */
    @Transactional
    public ProductCategoryResponseDTO updateCategory(Long id, ProductCategoryCreateDTO updateDTO) {
        log.info("🔄 Updating category ID: {}", id);
        
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + id));
        
        // Check if new name conflicts with existing categories (excluding current)
        if (!category.getName().equalsIgnoreCase(updateDTO.getName()) && 
            categoryRepository.existsByNameIgnoreCaseAndIdNot(updateDTO.getName(), id)) {
            throw new DuplicateEntityException("Category with name '" + updateDTO.getName() + "' already exists");
        }
        
        categoryMapper.updateEntity(category, updateDTO);
        ProductCategory updatedCategory = categoryRepository.save(category);
        
        log.info("✅ Category updated successfully: {}", updatedCategory.getName());
        return categoryMapper.toResponseDTO(updatedCategory);
    }
    
    /**
     * Delete category (soft delete by marking as inactive)
     */
    @Transactional
    public void deleteCategory(Long id) {
        log.info("🗑️ Soft deleting category ID: {}", id);
        
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + id));
        
        // Check if category is being used by products
        long productCount = productRepository.countByCategoryIgnoreCase(category.getName());
        if (productCount > 0) {
            // Soft delete - mark as inactive
            category.setIsActive(false);
            categoryRepository.save(category);
            log.info("⚠️ Category marked as inactive due to {} products using it: {}", productCount, category.getName());
        } else {
            // Hard delete if no products use it
            categoryRepository.delete(category);
            log.info("✅ Category hard deleted: {}", category.getName());
        }
    }
    
    /**
     * Enrich category with product count
     */
    private ProductCategoryResponseDTO enrichWithProductCount(ProductCategory category) {
        ProductCategoryResponseDTO dto = categoryMapper.toResponseDTO(category);
        long productCount = productRepository.countByCategoryIgnoreCase(category.getName());
        dto.setProductCount(productCount);
        return dto;
    }
    
    /**
     * Initialize default categories for a tenant
     */
    @Transactional
    public void initializeDefaultCategories() {
        log.info("🏗️ Initializing default categories");
        
        if (categoryRepository.count() == 0) {
            String[] defaultCategories = {
                "Electronics", "Clothing", "Books", "Home & Garden", 
                "Sports", "Toys", "Food & Beverages", "Health & Beauty", 
                "Automotive", "Office Supplies", "Other"
            };
            
            for (int i = 0; i < defaultCategories.length; i++) {
                ProductCategory category = new ProductCategory();
                category.setName(defaultCategories[i]);
                category.setDescription("Default " + defaultCategories[i] + " category");
                category.setIsActive(true);
                category.setSortOrder(i);
                categoryRepository.save(category);
            }
            
            log.info("✅ Default categories initialized: {} categories", defaultCategories.length);
        }
    }
}
