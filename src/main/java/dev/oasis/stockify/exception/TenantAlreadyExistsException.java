package dev.oasis.stockify.exception;

/**
 * Exception thrown when attempting to create a tenant that already exists
 */
public class TenantAlreadyExistsException extends RuntimeException {
    
    public TenantAlreadyExistsException(String message) {
        super(message);
    }
    
    public TenantAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
