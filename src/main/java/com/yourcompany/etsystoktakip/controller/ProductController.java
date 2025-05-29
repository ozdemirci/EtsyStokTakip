package com.yourcompany.etsystoktakip.controller;

import com.yourcompany.etsystoktakip.dto.ProductCreateDTO;
import com.yourcompany.etsystoktakip.dto.ProductResponseDTO;
import com.yourcompany.etsystoktakip.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for product management operations
 */
@Controller
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Displays a paginated list of products
     * @param page the page number (0-based)
     * @param size the page size
     * @param model the model to add attributes to
     * @return the view name
     */
    @GetMapping
    public String listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<ProductResponseDTO> productPage = productService.getProductsPage(pageable);

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", productPage.getNumber());
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalItems", productPage.getTotalElements());
        model.addAttribute("pageSize", size);

        return "product-list";
    }

    /**
     * Displays the form for adding a new product
     */
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("product", new ProductCreateDTO());
        return "product-form";
    }

    /**
     * Processes the form submission to add a new product
     */
    @PostMapping("/add")
    public String addProduct(@ModelAttribute ProductCreateDTO productCreateDTO) {
        if (productCreateDTO == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        productService.saveProduct(productCreateDTO);
        return "redirect:/products";
    }

    /**
     * Displays the form for editing an existing product
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        ProductResponseDTO product = productService.getProductById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        model.addAttribute("product", product);
        model.addAttribute("isEdit", true);
        return "product-form";
    }

    /**
     * Processes the form submission to update an existing product
     */
    @PostMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id, @ModelAttribute ProductCreateDTO productCreateDTO) {
        productService.updateProduct(id, productCreateDTO);
        return "redirect:/products";
    }

    /**
     * Deletes a product
     */
    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "redirect:/products";
    }    

}
