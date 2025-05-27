/* CREATE TABLE IF NOT EXISTS Content (
    id SERIAL PRIMARY KEY ,
    title varchar(255) NOT NULL,
    description text,
    status VARCHAR(20) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    date_created TIMESTAMP NOT NULL,
    date_updated TIMESTAMP,
    url VARCHAR(255)
);

-- Insert data into 'Content (title, description, status, content_type, date_created, date_updated, url) VALUES (value1, value2);
INSERT INTO Content (title, description, status, content_type, date_created, date_updated, url) 
VALUES ("value1", "value2", "value3", "value4", NOW(), NOW(), "value5"); */