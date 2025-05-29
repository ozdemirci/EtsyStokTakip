package com.yourcompany.etsystoktakip.service;

import com.yourcompany.etsystoktakip.dto.ProductCreateDTO;
import com.yourcompany.etsystoktakip.dto.ProductResponseDTO;
import com.yourcompany.etsystoktakip.mapper.ProductMapper;
import com.yourcompany.etsystoktakip.model.Product;
import com.yourcompany.etsystoktakip.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing product operations
 */
@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    /**
     * Retrieves all products from the database
     * @return a list of all products
     */
    public List<ProductResponseDTO> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return productMapper.toDtoList(products);
    }

    /**
     * Retrieves a page of products from the database
     * @param pageable pagination information
     * @return a page of products
     */
    public Page<ProductResponseDTO> getProductsPage(Pageable pageable) {
        Page<Product> productPage = productRepository.findAll(pageable);
        List<ProductResponseDTO> productDtos = productMapper.toDtoList(productPage.getContent());
        return new PageImpl<>(productDtos, pageable, productPage.getTotalElements());
    }

    /**
     * Retrieves a product by its ID
     * @param id the ID of the product to retrieve
     * @return an Optional containing the product if found, or empty if not found
     */
    public Optional<ProductResponseDTO> getProductById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toDto);
    }

    /**
     * Saves a product to the database
     * @param productCreateDTO the product data to save
     * @return the saved product data
     */
    public ProductResponseDTO saveProduct(ProductCreateDTO productCreateDTO) {
        Product product = productMapper.toEntity(productCreateDTO);
        Product savedProduct = productRepository.save(product);
        return productMapper.toDto(savedProduct);
    }

    /**
     * Updates an existing product in the database
     * @param id the ID of the product to update
     * @param productCreateDTO the updated product data
     * @return the updated product data
     */
    public ProductResponseDTO updateProduct(Long id, ProductCreateDTO productCreateDTO) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    Product updatedProduct = productMapper.updateEntity(existingProduct, productCreateDTO);
                    return productRepository.save(updatedProduct);
                })
                .map(productMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    /**
     * Deletes a product by its ID
     * @param id the ID of the product to delete
     */
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}
