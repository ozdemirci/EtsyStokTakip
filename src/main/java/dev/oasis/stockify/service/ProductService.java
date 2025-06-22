package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.dto.QuickRestockResponseDTO;
import dev.oasis.stockify.mapper.ProductMapper;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing product operations
 */
@Service
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final StockNotificationService stockNotificationService;

    public ProductService(ProductRepository productRepository,
                        ProductMapper productMapper,
                        StockNotificationService stockNotificationService) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.stockNotificationService = stockNotificationService;
    }    /**
     * Retrieves all products from the database
     * @return a list of all products
     */
    public List<ProductResponseDTO> getAllProducts() {
        String currentTenant = TenantContext.getCurrentTenant();
        List<Product> products = productRepository.findAll();
        log.debug("📦 Getting all products for tenant: {} - Found {} products", 
                 currentTenant, products.size());
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
     * Searches for products by title or category
     * @param searchTerm the search term to match against title or category
     * @param pageable pagination information
     * @return a page of matching products
     */
    public Page<ProductResponseDTO> searchProducts(String searchTerm, Pageable pageable) {
        Page<Product> productPage = productRepository.search(searchTerm, pageable);
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
     * Checks if a SKU already exists in the database
     * @param sku the SKU to check
     * @return true if the SKU exists, false otherwise
     */
    public boolean isSkuExists(String sku) {
        return productRepository.findBySku(sku).isPresent();
    }

    /**
     * Saves a product to the database
     * @param productCreateDTO the product data to save
     * @return the saved product data
     */
    @Transactional
    public ProductResponseDTO saveProduct(ProductCreateDTO productCreateDTO) {
        validateProductData(productCreateDTO);

        if (isSkuExists(productCreateDTO.getSku())) {
            throw new IllegalArgumentException("SKU '" + productCreateDTO.getSku() + "' is already in use");
        }

        try {
            Product product = productMapper.toEntity(productCreateDTO);
            Product savedProduct = productRepository.save(product);
            stockNotificationService.checkAndCreateLowStockNotification(savedProduct);
            return productMapper.toDto(savedProduct);
        } catch (Exception e) {
            throw new RuntimeException("Error saving product: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing product in the database
     * @param id the ID of the product to update
     * @param productCreateDTO the updated product data
     * @return the updated product data
     */
    @Transactional
    public ProductResponseDTO updateProduct(Long id, ProductCreateDTO productCreateDTO) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }

        try {
            Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

            validateProductData(productCreateDTO);
            Product updatedProduct = productMapper.updateEntity(existingProduct, productCreateDTO);
            Product saved = productRepository.saveAndFlush(updatedProduct); // Değişiklik burada
            stockNotificationService.checkAndCreateLowStockNotification(saved);

            return productMapper.toDto(saved);
        } catch (Exception e) {
            throw new RuntimeException("Error updating product: " + e.getMessage(), e);
        }
    }

    private void validateProductData(ProductCreateDTO productCreateDTO) {
        if (productCreateDTO == null) {
            throw new IllegalArgumentException("Product data cannot be null");
        }
        if (productCreateDTO.getTitle() == null || productCreateDTO.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Product title cannot be empty");
        }
        if (productCreateDTO.getPrice() == null || productCreateDTO.getPrice().doubleValue() < 0) {
            throw new IllegalArgumentException("Product price must be valid");
        }
        if (productCreateDTO.getStockLevel() < 0) {
            throw new IllegalArgumentException("Stock level cannot be negative");
        }
        if (productCreateDTO.getCategory() == null || productCreateDTO.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Product category cannot be empty");
        }
    }

    /**
     * Updates the stock level of a product
     * @param id the ID of the product
     * @param newStockLevel the new stock level
     * @return the updated product data
     */
    @Transactional
    public ProductResponseDTO updateStockLevel(Long id, int newStockLevel) {
        return productRepository.findById(id)
                .map(product -> {
                    product.setStockLevel(newStockLevel);
                    Product saved = productRepository.save(product);
                    stockNotificationService.checkAndCreateLowStockNotification(saved);
                    return productMapper.toDto(saved);
                })
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    /**
     * Deletes a product by its ID
     * @param id the ID of the product to delete
     */
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    /**
     * Checks if a SKU exists for any product other than the one being edited
     * @param productId the ID of the product being edited
     * @param sku the SKU to check
     * @return true if the SKU exists for another product, false otherwise
     */
    public boolean isSkuExistsForOtherProduct(Long productId, String sku) {
        return productRepository.findBySku(sku)
                .map(product -> !product.getId().equals(productId))
                .orElse(false);
    }

    /**
     * Quick restock operation - adds quantity to existing stock or sets new stock level
     * @param productId the ID of the product to restock
     * @param quantity the quantity to add or set
     * @param operation "ADD" to add to existing stock, "SET" to set new stock level
     * @return the updated product data with old and new stock levels
     */
    @Transactional
    public QuickRestockResponseDTO quickRestock(Long productId, Integer quantity, String operation) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.info("🔄 Quick restock for product ID: {} with quantity: {} operation: {} for tenant: {}", 
                productId, quantity, operation, currentTenant);
        
        return productRepository.findById(productId)
                .map(product -> {
                    Integer oldStockLevel = product.getStockLevel();
                    Integer newStockLevel;
                    
                    switch (operation.toUpperCase()) {
                        case "ADD":
                            newStockLevel = oldStockLevel + quantity;
                            break;
                        case "SET":
                            newStockLevel = quantity;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid operation: " + operation + ". Use 'ADD' or 'SET'");
                    }
                    
                    // Validate new stock level
                    if (newStockLevel < 0) {
                        throw new IllegalArgumentException("Stock level cannot be negative");
                    }
                    
                    // Update stock level
                    product.setStockLevel(newStockLevel);
                    Product savedProduct = productRepository.save(product);
                    
                    // Check for low stock notifications
                    stockNotificationService.checkAndCreateLowStockNotification(savedProduct);
                    
                    log.info("✅ Quick restock completed - Product: {} Old Stock: {} New Stock: {} for tenant: {}", 
                            product.getTitle(), oldStockLevel, newStockLevel, currentTenant);
                    
                    return QuickRestockResponseDTO.success(
                        productId,
                        product.getTitle(),
                        oldStockLevel,
                        newStockLevel,
                        operation.equals("ADD") ? quantity : newStockLevel - oldStockLevel,
                        operation
                    );
                })
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
    }
}
