-- =====================================================================================
-- STOCKIFY CONSOLIDATED MIGRATION SCRIPT
-- Version: V1__init_complete_schema.sql
-- 
-- This migration creates all required tables for the Stockify multi-tenant system.
-- PostgreSQL-compatible version for production deployment.
-- =====================================================================================

-- =====================================================================================
-- 1. APP_USER TABLE - User management for all tenants
-- =====================================================================================
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(20) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
    email VARCHAR(255),
    
    -- Super Admin specific fields
    can_manage_all_tenants BOOLEAN DEFAULT FALSE,
    accessible_tenants VARCHAR(1000),
    is_global_user BOOLEAN DEFAULT FALSE,
    primary_tenant VARCHAR(50),
    
    -- Status and audit fields
    is_active BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster username lookups
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_app_user_role ON app_user(role);
CREATE INDEX IF NOT EXISTS idx_app_user_primary_tenant ON app_user(primary_tenant);

-- =====================================================================================
-- 2. PRODUCT_CATEGORIES TABLE - Product category management
-- =====================================================================================
CREATE TABLE IF NOT EXISTS product_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    hex_color VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index for active categories
CREATE INDEX IF NOT EXISTS idx_product_categories_active ON product_categories(is_active);
CREATE INDEX IF NOT EXISTS idx_product_categories_sort_order ON product_categories(sort_order);

-- =====================================================================================
-- 3. PRODUCT TABLE - Core inventory management
-- =====================================================================================
CREATE TABLE IF NOT EXISTS product (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(255) UNIQUE,
    title VARCHAR(255),
    description TEXT,
    category VARCHAR(255),
    price DECIMAL(19,2),
    stock_level INTEGER,
    low_stock_threshold INTEGER,
    etsy_product_id VARCHAR(255),
    
    -- Status fields
    is_active BOOLEAN DEFAULT TRUE,
    is_featured BOOLEAN DEFAULT FALSE,
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_product_sku ON product(sku);
CREATE INDEX IF NOT EXISTS idx_product_category ON product(category);
CREATE INDEX IF NOT EXISTS idx_product_stock_level ON product(stock_level);
CREATE INDEX IF NOT EXISTS idx_product_is_active ON product(is_active);
CREATE INDEX IF NOT EXISTS idx_product_created_at ON product(created_at);

-- =====================================================================================
-- 4. STOCK_MOVEMENT TABLE - Inventory movement tracking
-- =====================================================================================
CREATE TABLE IF NOT EXISTS stock_movement (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(50) NOT NULL CHECK (movement_type IN ('IN', 'OUT', 'ADJUSTMENT', 'SALE', 'PURCHASE', 'RETURN')),
    quantity INTEGER NOT NULL,
    previous_stock INTEGER NOT NULL,
    new_stock INTEGER NOT NULL,
    reference_id VARCHAR(255),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

-- Create indexes for stock movement queries
CREATE INDEX IF NOT EXISTS idx_stock_movement_product_id ON stock_movement(product_id);
CREATE INDEX IF NOT EXISTS idx_stock_movement_type ON stock_movement(movement_type);
CREATE INDEX IF NOT EXISTS idx_stock_movement_created_at ON stock_movement(created_at);
CREATE INDEX IF NOT EXISTS idx_stock_movement_created_by ON stock_movement(created_by);

-- =====================================================================================
-- 5. STOCK_NOTIFICATION TABLE - Low stock and other notifications
-- =====================================================================================
CREATE TABLE IF NOT EXISTS stock_notification (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    message TEXT,
    notification_type VARCHAR(50) DEFAULT 'LOW_STOCK',
    is_read BOOLEAN DEFAULT FALSE,
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    category VARCHAR(50) DEFAULT 'STOCK_ALERT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    read_by BIGINT,
    
    -- Foreign key constraint
    CONSTRAINT fk_stock_notification_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

-- Create indexes for notification queries
CREATE INDEX IF NOT EXISTS idx_stock_notification_product_id ON stock_notification(product_id);
CREATE INDEX IF NOT EXISTS idx_stock_notification_is_read ON stock_notification(is_read);
CREATE INDEX IF NOT EXISTS idx_stock_notification_type ON stock_notification(notification_type);
CREATE INDEX IF NOT EXISTS idx_stock_notification_created_at ON stock_notification(created_at);

-- =====================================================================================
-- 6. TENANT_CONFIG TABLE - Tenant-specific configuration
-- =====================================================================================
CREATE TABLE IF NOT EXISTS tenant_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(20) DEFAULT 'STRING' CHECK (config_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'JSON')),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for config key lookups
CREATE INDEX IF NOT EXISTS idx_tenant_config_key ON tenant_config(config_key);
CREATE INDEX IF NOT EXISTS idx_tenant_config_type ON tenant_config(config_type);

-- =====================================================================================
-- 7. CONTACT_MESSAGES TABLE - Contact form submissions (PUBLIC SCHEMA ONLY)
-- This table should only exist in the public schema for global contact management
-- Skip this table creation for tenant schemas - it's created separately in public schema
-- =====================================================================================

-- Note: contact_messages table is NOT created here as it belongs only to public schema
-- It is created separately during application startup for global contact management

-- =====================================================================================
-- INITIAL DATA SETUP
-- =====================================================================================

-- Insert default tenant configuration values using PostgreSQL UPSERT syntax
INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES
('timezone', 'UTC', 'STRING', 'Default timezone for the tenant'),
('currency', 'USD', 'STRING', 'Default currency for the tenant'),
('low_stock_threshold', '5', 'INTEGER', 'Default low stock threshold'),
('email_notifications', 'true', 'BOOLEAN', 'Enable email notifications'),
('company_name', 'Default Company', 'STRING', 'Company name for this tenant'),
('admin_email', 'admin@company.com', 'STRING', 'Primary admin email'),
('tenant_status', 'ACTIVE', 'STRING', 'Tenant activation status'),
('subscription_plan', 'TRIAL', 'STRING', 'Tenant subscription plan')
ON CONFLICT (config_key) DO UPDATE SET 
    config_value = EXCLUDED.config_value,
    updated_at = CURRENT_TIMESTAMP;

-- Insert default product categories (avoid duplicates with WHERE NOT EXISTS)
INSERT INTO product_categories (name, description, is_active, sort_order, hex_color) 
SELECT 'Electronics', 'Electronic devices and gadgets', TRUE, 1, '#2563eb'
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE name = 'Electronics');

INSERT INTO product_categories (name, description, is_active, sort_order, hex_color) 
SELECT 'Accessories', 'Various accessories and add-ons', TRUE, 2, '#7c3aed'
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE name = 'Accessories');

INSERT INTO product_categories (name, description, is_active, sort_order, hex_color) 
SELECT 'Office Supplies', 'Office and business supplies', TRUE, 3, '#059669'
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE name = 'Office Supplies');

INSERT INTO product_categories (name, description, is_active, sort_order, hex_color) 
SELECT 'Home & Garden', 'Home improvement and garden items', TRUE, 4, '#dc2626'
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE name = 'Home & Garden');

INSERT INTO product_categories (name, description, is_active, sort_order, hex_color) 
SELECT 'Books & Media', 'Books, DVDs, and media content', TRUE, 5, '#ea580c'
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE name = 'Books & Media');

-- =====================================================================================
-- CREATE TRIGGER FOR UPDATED_AT TIMESTAMPS (PostgreSQL)
-- =====================================================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at columns
DROP TRIGGER IF EXISTS update_app_user_updated_at ON app_user;
CREATE TRIGGER update_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_product_categories_updated_at ON product_categories;
CREATE TRIGGER update_product_categories_updated_at
    BEFORE UPDATE ON product_categories
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_product_updated_at ON product;
CREATE TRIGGER update_product_updated_at
    BEFORE UPDATE ON product
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_tenant_config_updated_at ON tenant_config;
CREATE TRIGGER update_tenant_config_updated_at
    BEFORE UPDATE ON tenant_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================================================
-- MIGRATION COMPLETE
-- =====================================================================================
