package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.exception.FileOperationException;
import dev.oasis.stockify.service.ProductService;
import dev.oasis.stockify.service.ProductImportExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for product management operations
 */
@Controller
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;
    private final ProductImportExportService importExportService;

    public ProductController(ProductService productService, ProductImportExportService importExportService) {
        this.productService = productService;
        this.importExportService = importExportService;
    }

    /**
     * Displays a paginated list of products
     * @param page the page number (0-based)
     * @param size the page size
     * @param search the search query
     * @param model the model to add attributes to
     * @return the view name
     */
    @GetMapping
    public String listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<ProductResponseDTO> productPage;

        if (search != null && !search.trim().isEmpty()) {
            productPage = productService.searchProducts(search.trim(), pageable);
        } else {
            productPage = productService.getProductsPage(pageable);
        }

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
    public String addProduct(@ModelAttribute ProductCreateDTO productCreateDTO,
                           RedirectAttributes redirectAttributes) {
        try {
            // Temel validasyonlar
            if (productCreateDTO == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Product data cannot be null");
                return "redirect:/products/add";
            }

            if (productCreateDTO.getTitle() == null || productCreateDTO.getTitle().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Product title cannot be empty");
                return "redirect:/products/add";
            }

            if (productCreateDTO.getPrice() == null || productCreateDTO.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "Product price must be valid");
                return "redirect:/products/add";
            }

            if (productCreateDTO.getStockLevel() < 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "Stock level cannot be negative");
                return "redirect:/products/add";
            }

            if (productCreateDTO.getSku() == null || productCreateDTO.getSku().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "SKU cannot be empty");
                return "redirect:/products/add";
            }

            // SKU benzersizlik kontrolü
            if (productService.isSkuExists(productCreateDTO.getSku())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    String.format("SKU '%s' is already in use", productCreateDTO.getSku()));
                return "redirect:/products/add";
            }

            ProductResponseDTO savedProduct = productService.saveProduct(productCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage",
                String.format("Product '%s' was successfully created", savedProduct.getTitle()));
            return "redirect:/products";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error creating product: " + e.getMessage());
            return "redirect:/products/add";
        }
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
    public String editProduct(@PathVariable Long id,
                            @ModelAttribute("product") ProductCreateDTO productCreateDTO,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        try {
            // Ürünün var olup olmadığını kontrol et
            ProductResponseDTO existingProduct = productService.getProductById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

            // Temel validasyonlar
            if (productCreateDTO.getTitle() == null || productCreateDTO.getTitle().trim().isEmpty()) {
                model.addAttribute("errorMessage", "Product title cannot be empty");
                model.addAttribute("isEdit", true);
                return "product-form";
            }

            if (productCreateDTO.getPrice() == null || productCreateDTO.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                model.addAttribute("errorMessage", "Product price must be valid");
                model.addAttribute("isEdit", true);
                return "product-form";
            }

            if (productCreateDTO.getStockLevel() < 0) {
                model.addAttribute("errorMessage", "Stock level cannot be negative");
                model.addAttribute("isEdit", true);
                return "product-form";
            }

            if (productCreateDTO.getSku() == null || productCreateDTO.getSku().trim().isEmpty()) {
                model.addAttribute("errorMessage", "SKU cannot be empty");
                model.addAttribute("isEdit", true);
                return "product-form";
            }

            // SKU benzersizlik kontrolü (mevcut ürün hariç)
            if (productService.isSkuExistsForOtherProduct(id, productCreateDTO.getSku())) {
                model.addAttribute("errorMessage",
                    String.format("SKU '%s' is already in use by another product", productCreateDTO.getSku()));
                model.addAttribute("isEdit", true);
                return "product-form";
            }

            ProductResponseDTO updatedProduct = productService.updateProduct(id, productCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage",
                String.format("Product '%s' was successfully updated", updatedProduct.getTitle()));
            return "redirect:/products";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error updating product: " + e.getMessage());
            model.addAttribute("isEdit", true);
            return "product-form";
        }
    }

    /**
     * Deletes a product
     */
    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "redirect:/products";
    }    

    /**
     * Handles CSV file import
     */
    @PostMapping("/import/csv")
    public String importProductsFromCsv(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            List<ProductResponseDTO> importedProducts = importExportService.importProductsFromCsv(file);
            redirectAttributes.addFlashAttribute("successMessage", 
                String.format("Successfully imported %d products", importedProducts.size()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error importing products: " + e.getMessage());
        }
        return "redirect:/products";
    }

    /**
     * Handles Excel file import
     */
    @PostMapping("/import/excel")
    public String importProductsFromExcel(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            List<ProductResponseDTO> importedProducts = importExportService.importProductsFromExcel(file);
            redirectAttributes.addFlashAttribute("successMessage", 
                String.format("Successfully imported %d products", importedProducts.size()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error importing products: " + e.getMessage());
        }
        return "redirect:/products";
    }

    /**
     * Exports products to CSV
     */
    @GetMapping("/export/csv")
    public void exportProductsToCsv(jakarta.servlet.http.HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"products.csv\"");
        
        List<ProductResponseDTO> products = productService.getAllProducts();
        importExportService.exportProductsToCsv(response.getWriter(), products);
    }

    /**
     * Exports products to Excel
     */
    @GetMapping("/export/excel")
    public void exportProductsToExcel(jakarta.servlet.http.HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"products.xlsx\"");
        
        List<ProductResponseDTO> products = productService.getAllProducts();
        importExportService.exportProductsToExcel(response.getOutputStream(), products);
    }

    /**
     * Returns the CSV template file for product imports
     */
    @GetMapping("/import/template/csv")
    public void getImportTemplateCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"product_import_template.csv\"");
        
        // Copy template file to response
        try (InputStream is = getClass().getResourceAsStream("/static/templates/product_import_template.csv")) {
            if (is == null) {
                throw new FileOperationException("CSV template file not found");
            }
            StreamUtils.copy(is, response.getOutputStream());
        }
    }

    /**
     * Returns the Excel template file for product imports
     */
    @GetMapping("/import/template/excel")
    public void getImportTemplateExcel(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"product_import_template.xlsx\"");
        importExportService.generateExcelTemplate(response.getOutputStream());
    }

}
