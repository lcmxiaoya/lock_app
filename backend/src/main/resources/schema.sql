-- Smart Lock Database Schema (PostgreSQL)

-- Create database (run this manually in PostgreSQL)
-- CREATE DATABASE smart_lock;

-- User table
CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    tt_username VARCHAR(200),
    tt_password VARCHAR(100),
    tt_uid INTEGER,
    tt_access_token VARCHAR(500),
    tt_refresh_token VARCHAR(500),
    tt_token_expires_at BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Lock table
CREATE TABLE IF NOT EXISTS "lock" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id INTEGER NOT NULL UNIQUE,
    lock_name VARCHAR(100),
    lock_alias VARCHAR(100),
    lock_mac VARCHAR(50),
    lock_data TEXT,
    key_id INTEGER,
    electric_quantity INTEGER DEFAULT 0,
    keyboard_pwd_version INTEGER DEFAULT 4,
    special_value INTEGER DEFAULT 0,
    lock_version TEXT,
    group_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lock_user_id ON "lock"(user_id);

-- EKey table (Electronic Key)
CREATE TABLE IF NOT EXISTS ekey (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id BIGINT NOT NULL,
    key_id INTEGER NOT NULL UNIQUE,
    lock_data TEXT,
    key_name VARCHAR(100),
    key_type VARCHAR(20) NOT NULL,
    start_date BIGINT DEFAULT 0,
    end_date BIGINT DEFAULT 0,
    remarks VARCHAR(500),
    remote_enable INTEGER DEFAULT 2,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ekey_user_id ON ekey(user_id);
CREATE INDEX IF NOT EXISTS idx_ekey_lock_id ON ekey(lock_id);

-- Passcode table
CREATE TABLE IF NOT EXISTS passcode (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id BIGINT NOT NULL,
    keyboard_pwd_id INTEGER,
    keyboard_pwd VARCHAR(20),
    keyboard_pwd_name VARCHAR(100),
    pwd_type INTEGER NOT NULL,
    is_custom SMALLINT DEFAULT 0,
    start_date BIGINT DEFAULT 0,
    end_date BIGINT DEFAULT 0,
    status INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_passcode_user_id ON passcode(user_id);
CREATE INDEX IF NOT EXISTS idx_passcode_lock_id ON passcode(lock_id);

-- Record table (Operation records)
CREATE TABLE IF NOT EXISTS record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    record_type INTEGER,
    result VARCHAR(20) NOT NULL,
    record_time BIGINT,
    keyboard_pwd VARCHAR(50),
    password_name VARCHAR(200),
    fail_reason VARCHAR(100),
    uid BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(lock_id, action, record_time)
);

CREATE INDEX IF NOT EXISTS idx_record_user_id ON record(user_id);
CREATE INDEX IF NOT EXISTS idx_record_lock_id ON record(lock_id);

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_updated_at BEFORE UPDATE ON "user"
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_lock_updated_at BEFORE UPDATE ON "lock"
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
