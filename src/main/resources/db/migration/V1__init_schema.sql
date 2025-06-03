-- Create AppUser table
CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(20) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

-- Create Product table
CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(50) NOT NULL,
    description VARCHAR(1000),
    sku VARCHAR(50) NOT NULL UNIQUE,
    category VARCHAR(255) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock_level INT NOT NULL,
    low_stock_threshold INT NOT NULL,
    etsy_product_id VARCHAR(255)
);

-- Create StockNotification table
CREATE TABLE stock_notification (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    message VARCHAR(255) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(id)
);
