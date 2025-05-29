package dev.oasis.stockify.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@ControllerAdvice
public class GlobalExceptionHandler {

    // Custom exception class (you'll need to create this)
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    // Error response structure
    private Map<String, Object> createErrorResponse(String error, String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return body;
    }

    // Handle authentication exceptions
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Object> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        System.out.println("UsernameNotFoundException handler invoked");
        Map<String, Object> body = createErrorResponse(
            "User Not Found", 
            ex.getMessage(), 
            HttpStatus.NOT_FOUND
        );
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex) {
        Map<String, Object> body = createErrorResponse(
            "Authentication Failed", 
            "Invalid username or password", 
            HttpStatus.UNAUTHORIZED
        );
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex) {
        System.out.println("AccessDeniedException handler invoked"); // Debugging
        ex.printStackTrace(); // Debugging: Print stack trace to identify the root cause
        Map<String, Object> body = createErrorResponse(
            "Access Denied", 
            "You don't have permission to access this resource", 
            HttpStatus.FORBIDDEN
        );
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    // Handle validation exceptions
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", "Input validation failed");
        body.put("validationErrors", errors);
        
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBindException(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Binding Failed");
        body.put("message", "Request binding failed");
        body.put("validationErrors", errors);
        
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // Handle request parameter exceptions
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> body = createErrorResponse(
            "Bad Request", 
            ex.getMessage(), 
            HttpStatus.BAD_REQUEST
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Invalid value '%s' for parameter '%s'.", 
            ex.getValue(), ex.getName());
        
        Map<String, Object> body = createErrorResponse(
            "Invalid Parameter Type", 
            message, 
            HttpStatus.BAD_REQUEST
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        
        Map<String, Object> body = createErrorResponse(
            "Missing Parameter", 
            message, 
            HttpStatus.BAD_REQUEST
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // Handle HTTP method exceptions
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = String.format("HTTP method '%s' is not supported for this endpoint. Supported methods: %s",
            ex.getMethod(), String.join(", ", ex.getSupportedMethods()));
        
        Map<String, Object> body = createErrorResponse(
            "Method Not Allowed", 
            message, 
            HttpStatus.METHOD_NOT_ALLOWED
        );
        return new ResponseEntity<>(body, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        System.out.println("HttpMessageNotReadableException handler invoked");
        System.out.println("HttpMessageNotReadableException handler invoked with message: " + ex.getMessage());
        Map<String, Object> body = createErrorResponse(
            "Malformed JSON", 
            "Request body contains invalid JSON", 
            HttpStatus.BAD_REQUEST
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // Handle database exceptions
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, Object> body = createErrorResponse(
            "Data Integrity Violation", 
            "The operation violates database constraints", 
            HttpStatus.CONFLICT
        );
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    // Handle custom exceptions
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, Object> body = createErrorResponse(
            "Resource Not Found", 
            ex.getMessage(), 
            HttpStatus.NOT_FOUND
        );
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    // You can add more custom exception handlers here
    // Example for business logic exceptions:
    /*
    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<Object> handleBusinessLogicException(BusinessLogicException ex) {
        logger.warn("Business logic error: {}", ex.getMessage());
        Map<String, Object> body = createErrorResponse(
            "Business Logic Error", 
            ex.getMessage(), 
            HttpStatus.UNPROCESSABLE_ENTITY
        );
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    */
}