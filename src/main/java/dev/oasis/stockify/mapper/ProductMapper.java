package dev.oasis.stockify.mapper;

import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.model.Product;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper class for converting between Product entity and Product DTOs
 */
@Component
public class ProductMapper {

    /**
     * Converts a ProductCreateDTO to a Product entity
     * @param productCreateDTO the DTO to convert
     * @return the Product entity
     */
    public Product toEntity(ProductCreateDTO productCreateDTO) {
        if (productCreateDTO == null) {
            return null;
        }        Product product = new Product();
        product.setTitle(productCreateDTO.getTitle());
        product.setDescription(productCreateDTO.getDescription());
        product.setSku(productCreateDTO.getSku());
        product.setCategory(productCreateDTO.getCategory());        product.setPrice(productCreateDTO.getPrice());
        product.setStockLevel(productCreateDTO.getStockLevel());
        product.setLowStockThreshold(productCreateDTO.getLowStockThreshold());
        product.setEtsyProductId(productCreateDTO.getEtsyProductId());
        product.setIsActive(productCreateDTO.getIsActive() != null ? productCreateDTO.getIsActive() : true);
        product.setIsFeatured(productCreateDTO.getIsFeatured() != null ? productCreateDTO.getIsFeatured() : false);
        
        // Set audit fields
        product.setCreatedAt(productCreateDTO.getCreatedAt());
        product.setUpdatedAt(productCreateDTO.getUpdatedAt());
        product.setCreatedBy(productCreateDTO.getCreatedBy());
        product.setUpdatedBy(productCreateDTO.getUpdatedBy());

        return product;
    }

    /**
     * Updates an existing Product entity with data from a ProductCreateDTO
     * @param product the entity to update
     * @param productCreateDTO the DTO with updated data
     * @return the updated Product entity
     */
    public Product updateEntity(Product product, ProductCreateDTO productCreateDTO) {
        if (productCreateDTO == null) {
            return product;
        }        product.setTitle(productCreateDTO.getTitle());
        product.setDescription(productCreateDTO.getDescription());
        product.setCategory(productCreateDTO.getCategory());
        product.setPrice(productCreateDTO.getPrice());
        product.setStockLevel(productCreateDTO.getStockLevel());
        product.setLowStockThreshold(productCreateDTO.getLowStockThreshold());
          // Update isActive and isFeatured if provided
        if (productCreateDTO.getIsActive() != null) {
            product.setIsActive(productCreateDTO.getIsActive());
        }
        if (productCreateDTO.getIsFeatured() != null) {
            product.setIsFeatured(productCreateDTO.getIsFeatured());
        }

        // SKU değişmişse ve yeni SKU null değilse güncelle
        if (productCreateDTO.getSku() != null && !productCreateDTO.getSku().equals(product.getSku())) {
            product.setSku(productCreateDTO.getSku());
        }

        // Set audit fields for update
        if (productCreateDTO.getUpdatedBy() != null) {
            product.setUpdatedBy(productCreateDTO.getUpdatedBy());
        }
        if (productCreateDTO.getUpdatedAt() != null) {
            product.setUpdatedAt(productCreateDTO.getUpdatedAt());
        }



        return product;
    }

    /**
     * Converts a Product entity to a ProductResponseDTO
     * @param product the entity to convert
     * @return the ProductResponseDTO
     */
    public ProductResponseDTO toDto(Product product) {
        if (product == null) {
            return null;
        }
        ProductResponseDTO productResponseDTO = new ProductResponseDTO();
        productResponseDTO.setId(product.getId());
        productResponseDTO.setTitle(product.getTitle());
        productResponseDTO.setDescription(product.getDescription());
        productResponseDTO.setSku(product.getSku());
        productResponseDTO.setCategory(product.getCategory());        productResponseDTO.setPrice(product.getPrice());
        productResponseDTO.setStockLevel(product.getStockLevel());        productResponseDTO.setLowStockThreshold(product.getLowStockThreshold());        productResponseDTO.setEtsyProductId(product.getEtsyProductId());
        productResponseDTO.setIsActive(product.getIsActive());
        productResponseDTO.setIsFeatured(product.getIsFeatured());
        
        // Set audit fields
        productResponseDTO.setCreatedAt(product.getCreatedAt());
        productResponseDTO.setUpdatedAt(product.getUpdatedAt());
        productResponseDTO.setCreatedBy(product.getCreatedBy());
        productResponseDTO.setUpdatedBy(product.getUpdatedBy());

        return productResponseDTO;
    }

    /**
     * Converts a list of Product entities to a list of ProductResponseDTOs
     * @param products the list of entities to convert
     * @return the list of ProductResponseDTOs
     */
    public List<ProductResponseDTO> toDtoList(List<Product> products) {
        if (products == null) {
            return null;
        }
        
        return products.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}

