package dev.oasis.stockify.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.exception.FileOperationException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductImportExportService {
    private static final String[] CSV_HEADERS = {"Name", "Description", "SKU", "Price", "Quantity", "Category"};
    private static final List<String> REQUIRED_HEADERS = List.of(CSV_HEADERS);
    private static final int BATCH_SIZE = 100;

    private final ProductService productService;

    public List<ProductResponseDTO> importProductsFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileOperationException("The uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new FileOperationException("Only CSV files are supported for this operation");
        }

        List<ProductResponseDTO> importedProducts = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            String[] headers = reader.readNext();
            validateHeaders(headers);

            Map<String, Integer> headerMap = createHeaderMap(headers);
            String[] line;
            List<ProductCreateDTO> batch = new ArrayList<>(BATCH_SIZE);

            int lineNumber = 1;
            while ((line = reader.readNext()) != null) {
                lineNumber++;
                try {
                    ProductCreateDTO product = parseProductFromCsvLine(line, headerMap);
                    batch.add(product);

                    if (batch.size() >= BATCH_SIZE) {
                        importedProducts.addAll(processBatch(batch));
                        batch.clear();
                    }
                } catch (Exception e) {
                    throw new FileOperationException(String.format("Error in line %d: %s. Data: %s",
                            lineNumber, e.getMessage(), String.join(",", line)));
                }
            }

            if (!batch.isEmpty()) {
                importedProducts.addAll(processBatch(batch));
            }
        } catch (IOException | CsvValidationException e) {
            throw new FileOperationException("Error reading CSV file: " + e.getMessage());
        }

        return importedProducts;
    }

    /**
     * Export products to CSV file
     */
    public void exportProductsToCsv(Writer writer, List<ProductResponseDTO> products) {
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(CSV_HEADERS);

            for (ProductResponseDTO product : products) {
                String[] line = new String[]{
                        product.getTitle(),
                        product.getDescription(),
                        product.getSku(),
                        product.getPrice().toString(),
                        String.valueOf(product.getStockLevel()),
                        product.getCategory()
                };
                csvWriter.writeNext(line);
            }
            csvWriter.flush();
        } catch (IOException e) {
            throw new FileOperationException("Error writing to CSV: " + e.getMessage());
        }
    }

    /**
     * Import products from Excel file
     */
    public List<ProductResponseDTO> importProductsFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileOperationException("The uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls")) {
            throw new FileOperationException("Only Excel files (.xlsx or .xls) are supported for this operation");
        }

        List<ProductResponseDTO> importedProducts = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            validateExcelHeaders(headerRow);

            Map<String, Integer> headerMap = createExcelHeaderMap(headerRow);
            List<ProductCreateDTO> batch = new ArrayList<>(BATCH_SIZE);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    ProductCreateDTO product = parseProductFromExcelRow(row, headerMap);
                    batch.add(product);

                    if (batch.size() >= BATCH_SIZE) {
                        importedProducts.addAll(processBatch(batch));
                        batch.clear();
                    }
                } catch (Exception e) {
                    throw new FileOperationException(String.format("Error in row %d: %s", i + 1, e.getMessage()));
                }
            }

            if (!batch.isEmpty()) {
                importedProducts.addAll(processBatch(batch));
            }
        } catch (IOException e) {
            throw new FileOperationException("Error reading Excel file: " + e.getMessage());
        }

        return importedProducts;
    }

    /**
     * Export products to Excel file
     */
    public void exportProductsToExcel(OutputStream outputStream, List<ProductResponseDTO> products) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Products");

            // Create header row with styles
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < CSV_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(CSV_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            CellStyle numberStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            numberStyle.setDataFormat(format.getFormat("#,##0.00"));

            for (int i = 0; i < products.size(); i++) {
                Row row = sheet.createRow(i + 1);
                ProductResponseDTO product = products.get(i);

                row.createCell(0).setCellValue(product.getTitle());
                row.createCell(1).setCellValue(product.getDescription());
                row.createCell(2).setCellValue(product.getSku());

                Cell priceCell = row.createCell(3);
                priceCell.setCellValue(product.getPrice().doubleValue());
                priceCell.setCellStyle(numberStyle);

                row.createCell(4).setCellValue(product.getStockLevel());
                row.createCell(5).setCellValue(product.getCategory());
            }

            // Autosize columns
            for (int i = 0; i < CSV_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            outputStream.flush();
        } catch (IOException e) {
            throw new FileOperationException("Error writing to Excel: " + e.getMessage());
        }
    }

    /**
     * Generate Excel template file
     */
    public void generateExcelTemplate(OutputStream outputStream) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Products Template");

            // Create header row with styles
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < CSV_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(CSV_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create sample rows
            CellStyle numberStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            numberStyle.setDataFormat(format.getFormat("#,##0.00"));

            // Sample row 1
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Sample Product");
            row1.createCell(1).setCellValue("Sample product description");
            row1.createCell(2).setCellValue("SKU001");
            Cell priceCell1 = row1.createCell(3);
            priceCell1.setCellValue(99.99);
            priceCell1.setCellStyle(numberStyle);
            row1.createCell(4).setCellValue(100);
            row1.createCell(5).setCellValue("Electronics");

            // Sample row 2
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Sample Product 2");
            row2.createCell(1).setCellValue("Another product description");
            row2.createCell(2).setCellValue("SKU002");
            Cell priceCell2 = row2.createCell(3);
            priceCell2.setCellValue(49.99);
            priceCell2.setCellStyle(numberStyle);
            row2.createCell(4).setCellValue(50);
            row2.createCell(5).setCellValue("Home & Garden");

            // Set column widths automatically
            for (int i = 0; i < CSV_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            outputStream.flush();
        } catch (IOException e) {
            throw new FileOperationException("Error generating Excel template: " + e.getMessage());
        }
    }

    private void validateHeaders(String[] headers) {
        if (headers == null || headers.length < REQUIRED_HEADERS.size()) {
            throw new FileOperationException("Missing required headers. Expected: " + String.join(", ", REQUIRED_HEADERS));
        }

        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(required -> !containsIgnoreCase(headers, required))
                .collect(Collectors.toList());

        if (!missingHeaders.isEmpty()) {
            throw new FileOperationException("Missing required headers: " + String.join(", ", missingHeaders));
        }
    }

    private void validateExcelHeaders(Row headerRow) {
        if (headerRow == null) {
            throw new FileOperationException("Excel file has no header row");
        }

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING) {
                headers.add(cell.getStringCellValue());
            }
        }

        if (headers.size() < REQUIRED_HEADERS.size()) {
            throw new FileOperationException("Missing required headers. Expected: " + String.join(", ", REQUIRED_HEADERS));
        }

        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(required -> !containsIgnoreCase(headers, required))
                .collect(Collectors.toList());

        if (!missingHeaders.isEmpty()) {
            throw new FileOperationException("Missing required headers: " + String.join(", ", missingHeaders));
        }
    }

    private boolean containsIgnoreCase(String[] array, String target) {
        for (String s : array) {
            if (target.equalsIgnoreCase(s.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(List<String> list, String target) {
        return list.stream().anyMatch(s -> target.equalsIgnoreCase(s.trim()));
    }

    private Map<String, Integer> createHeaderMap(String[] headers) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i].trim().toLowerCase(), i);
        }
        return headerMap;
    }

    private Map<String, Integer> createExcelHeaderMap(Row headerRow) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING) {
                headerMap.put(cell.getStringCellValue().trim().toLowerCase(), cell.getColumnIndex());
            }
        }
        return headerMap;
    }

    private ProductCreateDTO parseProductFromCsvLine(String[] line, Map<String, Integer> headerMap) {
        ProductCreateDTO product = new ProductCreateDTO();

        product.setTitle(getRequiredValue(line, headerMap, "name", "Product name"));
        product.setDescription(getOptionalValue(line, headerMap, "description", ""));
        product.setSku(getRequiredValue(line, headerMap, "sku", "SKU"));
        product.setCategory(getRequiredValue(line, headerMap, "category", "Category"));

        String priceStr = getRequiredValue(line, headerMap, "price", "Price");
        try {
            product.setPrice(new BigDecimal(priceStr));
        } catch (NumberFormatException e) {
            throw new FileOperationException("Invalid price format: " + priceStr);
        }

        String quantityStr = getRequiredValue(line, headerMap, "quantity", "Quantity");
        try {
            product.setStockLevel(Integer.parseInt(quantityStr));
        } catch (NumberFormatException e) {
            throw new FileOperationException("Invalid quantity format: " + quantityStr);
        }

        // Set a default low stock threshold
        product.setLowStockThreshold(5);

        return product;
    }

    private ProductCreateDTO parseProductFromExcelRow(Row row, Map<String, Integer> headerMap) {
        ProductCreateDTO product = new ProductCreateDTO();

        product.setTitle(getRequiredCellValue(row, headerMap, "name", "Product name"));
        product.setDescription(getOptionalCellValue(row, headerMap, "description", ""));
        product.setSku(getRequiredCellValue(row, headerMap, "sku", "SKU"));
        product.setCategory(getRequiredCellValue(row, headerMap, "category", "Category"));

        Double price = getRequiredNumericCellValue(row, headerMap, "price", "Price");
        if (price != null) {
            product.setPrice(BigDecimal.valueOf(price));
        } else {
            throw new FileOperationException("Invalid price format");
        }

        Double quantity = getRequiredNumericCellValue(row, headerMap, "quantity", "Quantity");
        if (quantity != null) {
            product.setStockLevel(quantity.intValue());
        } else {
            throw new FileOperationException("Invalid quantity format");
        }

        // Set a default low stock threshold
        product.setLowStockThreshold(5);

        return product;
    }

    private String getRequiredValue(String[] line, Map<String, Integer> headerMap, String headerKey, String fieldName) {
        Integer index = headerMap.get(headerKey.toLowerCase());
        if (index == null || index >= line.length) {
            throw new FileOperationException("Missing required field: " + fieldName);
        }
        String value = line[index].trim();
        if (value.isEmpty()) {
            throw new FileOperationException("Empty required field: " + fieldName);
        }
        return value;
    }

    private String getOptionalValue(String[] line, Map<String, Integer> headerMap, String headerKey, String defaultValue) {
        Integer index = headerMap.get(headerKey.toLowerCase());
        if (index == null || index >= line.length) {
            return defaultValue;
        }
        String value = line[index].trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private String getRequiredCellValue(Row row, Map<String, Integer> headerMap, String headerKey, String fieldName) {
        Integer index = headerMap.get(headerKey.toLowerCase());
        if (index == null) {
            throw new FileOperationException("Missing required field: " + fieldName);
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            throw new FileOperationException("Empty required field: " + fieldName);
        }

        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            default -> throw new FileOperationException("Invalid cell type for " + fieldName);
        };

        if (value.isEmpty()) {
            throw new FileOperationException("Empty required field: " + fieldName);
        }
        return value;
    }

    private String getOptionalCellValue(Row row, Map<String, Integer> headerMap, String headerKey, String defaultValue) {
        Integer index = headerMap.get(headerKey.toLowerCase());
        if (index == null) {
            return defaultValue;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return defaultValue;
        }

        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            default -> defaultValue;
        };

        return value.isEmpty() ? defaultValue : value;
    }

    private Double getRequiredNumericCellValue(Row row, Map<String, Integer> headerMap, String headerKey, String fieldName) {
        Integer index = headerMap.get(headerKey.toLowerCase());
        if (index == null) {
            throw new FileOperationException("Missing required field: " + fieldName);
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            throw new FileOperationException("Empty required field: " + fieldName);
        }

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> Double.parseDouble(cell.getStringCellValue().trim());
                default -> throw new FileOperationException("Invalid cell type for " + fieldName);
            };
        } catch (NumberFormatException e) {
            throw new FileOperationException("Invalid number format for " + fieldName);
        }
    }

    private List<ProductResponseDTO> processBatch(List<ProductCreateDTO> batch) {
        return batch.stream()
                .map(productService::saveProduct)
                .collect(Collectors.toList());
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
