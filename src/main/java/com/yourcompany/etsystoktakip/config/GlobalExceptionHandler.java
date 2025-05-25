package com.yourcompany.etsystoktakip.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handle generic exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex, WebRequest request) {
        logger.error("An unexpected error occurred: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Handle specific exceptions (e.g., UsernameNotFoundException)
    @ExceptionHandler(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
    public ResponseEntity<Object> handleUsernameNotFoundException(org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
        logger.warn("Username not found: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "User Not Found");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    // Handle IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Invalid argument: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // Handle custom exceptions (e.g., ResourceNotFoundException)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Resource Not Found");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    // Add more exception handlers as needed
}
