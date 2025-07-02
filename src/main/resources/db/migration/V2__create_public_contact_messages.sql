-- =====================================================================================
-- PUBLIC SCHEMA CONTACT MESSAGES TABLE
-- Version: V2__create_public_contact_messages.sql
-- 
-- This migration creates the contact_messages table ONLY in the public schema.
-- This table is used globally across all tenants for contact form submissions.
-- =====================================================================================

-- Check if we're running in the public schema - skip if not
DO $$
BEGIN
    -- Only execute if current schema is public
    IF current_schema() = 'public' THEN
        -- =====================================================================================
        -- CONTACT_MESSAGES TABLE - Global contact form submissions
        -- =====================================================================================
        CREATE TABLE IF NOT EXISTS contact_messages (
            id BIGSERIAL PRIMARY KEY,
            first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL,
            email VARCHAR(255) NOT NULL,
            phone VARCHAR(20),
            company VARCHAR(200),
            subject VARCHAR(255) NOT NULL,
            message TEXT NOT NULL,
            ip_address VARCHAR(45),
            user_agent TEXT,
            is_read BOOLEAN DEFAULT FALSE,
            responded BOOLEAN DEFAULT FALSE,
            responded_at TIMESTAMP,
            responded_by BIGINT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        -- Create indexes for contact message queries
        CREATE INDEX IF NOT EXISTS idx_contact_messages_email ON contact_messages(email);
        CREATE INDEX IF NOT EXISTS idx_contact_messages_is_read ON contact_messages(is_read);
        CREATE INDEX IF NOT EXISTS idx_contact_messages_responded ON contact_messages(responded);
        CREATE INDEX IF NOT EXISTS idx_contact_messages_created_at ON contact_messages(created_at);
        
        RAISE NOTICE '✅ Created contact_messages table in PUBLIC schema';
    ELSE
        RAISE NOTICE '⏭️ Skipping contact_messages table creation - not in public schema (current: %)', current_schema();
    END IF;
END $$;

-- =====================================================================================
-- MIGRATION COMPLETE - PUBLIC CONTACT MESSAGES TABLE CREATED (IF IN PUBLIC SCHEMA)
-- =====================================================================================
