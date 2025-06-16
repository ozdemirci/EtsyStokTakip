package dev.oasis.stockify.model;

/**
 * User Role Enumeration
 * Defines the different levels of access in the Stockify application
 */
public enum Role {
    
    /**
     * Super Admin - Has access to all tenants and system-wide management
     */
    SUPER_ADMIN("SUPER_ADMIN", "Super Administrator", "Full system access across all tenants"),
    
    /**
     * Admin - Has full access within their tenant
     */
    ADMIN("ADMIN", "Administrator", "Full access within tenant"),
    
    /**
     * User - Standard user with limited access within their tenant
     */
    USER("USER", "User", "Standard user access within tenant");
    
    private final String code;
    private final String displayName;
    private final String description;
    
    Role(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get Role from string code
     */
    public static Role fromCode(String code) {
        for (Role role : Role.values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid role code: " + code);
    }
    
    /**
     * Check if role has super admin privileges
     */
    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
    
    /**
     * Check if role has admin privileges (includes super admin)
     */
    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
