-- Smart Lock Database Schema (MySQL 8.0+)
-- Used by WeChat Cloud Hosting

CREATE DATABASE IF NOT EXISTS smart_lock DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_lock;

-- User table
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    openid VARCHAR(64) UNIQUE,
    unionid VARCHAR(64),
    phone VARCHAR(20),
    login_type VARCHAR(20) DEFAULT 'password',
    tt_username VARCHAR(200),
    tt_password VARCHAR(100),
    tt_uid INT,
    tt_access_token VARCHAR(500),
    tt_refresh_token VARCHAR(500),
    tt_token_expires_at BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Lock table
CREATE TABLE IF NOT EXISTS `lock` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id INT NOT NULL UNIQUE,
    lock_name VARCHAR(100),
    lock_alias VARCHAR(100),
    lock_mac VARCHAR(50),
    lock_data TEXT,
    key_id INT,
    electric_quantity INT DEFAULT 0,
    keyboard_pwd_version INT DEFAULT 4,
    special_value INT DEFAULT 0,
    lock_version TEXT,
    group_id INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_lock_user_id ON `lock`(user_id);

-- EKey table (Electronic Key)
CREATE TABLE IF NOT EXISTS ekey (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id BIGINT NOT NULL,
    key_id INT NOT NULL UNIQUE,
    lock_data TEXT,
    key_name VARCHAR(100),
    key_type VARCHAR(20) NOT NULL,
    start_date BIGINT DEFAULT 0,
    end_date BIGINT DEFAULT 0,
    remarks VARCHAR(500),
    remote_enable INT DEFAULT 2,
    status VARCHAR(20) DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ekey_user_id ON ekey(user_id);
CREATE INDEX idx_ekey_lock_id ON ekey(lock_id);

-- Passcode table
CREATE TABLE IF NOT EXISTS passcode (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id BIGINT NOT NULL,
    keyboard_pwd_id INT,
    keyboard_pwd VARCHAR(20),
    keyboard_pwd_name VARCHAR(100),
    pwd_type INT NOT NULL,
    is_custom SMALLINT DEFAULT 0,
    start_date BIGINT DEFAULT 0,
    end_date BIGINT DEFAULT 0,
    status INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_passcode_user_id ON passcode(user_id);
CREATE INDEX idx_passcode_lock_id ON passcode(lock_id);

-- Record table (Operation records)
CREATE TABLE IF NOT EXISTS record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    record_type INT,
    result VARCHAR(20) NOT NULL,
    record_time BIGINT,
    keyboard_pwd VARCHAR(50),
    password_name VARCHAR(200),
    fail_reason VARCHAR(100),
    uid BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lock_action_time (lock_id, action, record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_record_user_id ON record(user_id);
CREATE INDEX idx_record_lock_id ON record(lock_id);
