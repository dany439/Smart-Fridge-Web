USE smart_fridge;

-- DROP TABLE IF EXISTS recipes;
-- DROP TABLE IF EXISTS fridge_items;
-- DROP TABLE IF EXISTS roles;
-- DROP TABLE IF EXISTS users_info;


CREATE TABLE IF NOT EXISTS users_info (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin  UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS roles (
    user_id INT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_authorities_users FOREIGN KEY(user_id) REFERENCES users_info(id) ON DELETE CASCADE
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fridge_items(
	id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    name VARCHAR(25) NOT NULL,
    category VARCHAR(50),
    quantity DECIMAL(10,2) NOT NULL,
    unit VARCHAR(20),
    storage_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- instead of created_at
    expiry_date TIMESTAMP,
    added_via VARCHAR(10), -- MANUAL OR IMAGE
    CONSTRAINT fk_fridge_user FOREIGN KEY(user_id) REFERENCES users_info(id) ON DELETE CASCADE ON UPDATE CASCADE
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recipes (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL UNIQUE,
    saved_response TEXT,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_recipes_user FOREIGN KEY (user_id) REFERENCES users_info(id) ON DELETE CASCADE
);


-- 1. Insert the admin account credentials into users_info 
-- password: ADMIN123
INSERT INTO users_info (username, password, first_name, last_name, email)
VALUES ('admin', '$2a$12$e0L5JdSb2VGSMhxaVt1wT.9dV381TqH1ff0Ycn7Fmr87iXoPHu6wu', 'System', 'Admin', 'admin@smartfridge.com');

-- 2. Bind the role to the auto-incremented user ID generated above
INSERT INTO roles (user_id, role)
VALUES (LAST_INSERT_ID(), 'ROLE_ADMIN');

