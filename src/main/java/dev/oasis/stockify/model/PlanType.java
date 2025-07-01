package dev.oasis.stockify.model;

/**
 * Enum for subscription plan types
 */
public enum PlanType {
    TRIAL("trial", 2, 100, 30, "Trial Plan"),
    BASIC("basic", 5, 1000, -1, "Basic Plan"),
    PREMIUM("premium", 20, -1, -1, "Premium Plan"),
    ENTERPRISE("enterprise", -1, -1, -1, "Enterprise Plan");
    
    private final String code;
    private final int maxUsers;
    private final int maxProducts;
    private final int trialDays;
    private final String displayName;
    
    PlanType(String code, int maxUsers, int maxProducts, int trialDays, String displayName) {
        this.code = code;
        this.maxUsers = maxUsers;
        this.maxProducts = maxProducts;
        this.trialDays = trialDays;
        this.displayName = displayName;
    }
    
    public String getCode() { 
        return code; 
    }
    
    public int getMaxUsers() { 
        return maxUsers; 
    }
    
    public int getMaxProducts() { 
        return maxProducts; 
    }
    
    public int getTrialDays() { 
        return trialDays; 
    }
    
    public String getDisplayName() { 
        return displayName; 
    }
    
    public boolean isUnlimitedUsers() {
        return maxUsers == -1;
    }
    
    public boolean isUnlimitedProducts() {
        return maxProducts == -1;
    }
    
    public boolean isTrial() {
        return trialDays > 0;
    }
    
    /**
     * Get plan from code string
     */
    public static PlanType fromCode(String code) {
        if (code == null) {
            return TRIAL; // Default fallback
        }
        
        for (PlanType plan : values()) {
            if (plan.code.equalsIgnoreCase(code)) {
                return plan;
            }
        }
        return TRIAL; // Default fallback
    }
    
    /**
     * Get plan features description
     */
    public String getFeaturesDescription() {
        StringBuilder features = new StringBuilder();
        
        if (isUnlimitedUsers()) {
            features.append("Sınırsız Kullanıcı");
        } else {
            features.append(maxUsers).append(" Kullanıcı");
        }
        
        features.append(", ");
        
        if (isUnlimitedProducts()) {
            features.append("Sınırsız Ürün");
        } else {
            features.append(maxProducts).append(" Ürün");
        }
        
        switch (this) {
            case TRIAL -> features.append(", Temel Raporlar");
            case BASIC -> features.append(", Temel Raporlar");
            case PREMIUM -> features.append(", Gelişmiş Raporlar");
            case ENTERPRISE -> features.append(", Özel Raporlar");
        }
        
        return features.toString();
    }
    
    /**
     * Get plan price description
     */
    public String getPriceDescription() {
        return switch (this) {
            case TRIAL -> "₺0/ay (30 gün deneme)";
            case BASIC -> "₺199/ay";
            case PREMIUM -> "₺499/ay";
            case ENTERPRISE -> "₺999/ay";
        };
    }
}
