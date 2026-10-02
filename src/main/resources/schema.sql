-- ==========================================================
-- Paytm Project: Seat Reservation Database Schema
-- ==========================================================

-- 1. Users Table (Authentication & Identity)
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100),
    full_name   VARCHAR(100),
    role        VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER'
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);


-- 2. Shows Table (Event & Pricing Metadata)
CREATE TABLE IF NOT EXISTS shows (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    price_paise     BIGINT NOT NULL,
    per_user_limit  INT NOT NULL DEFAULT 4,
    total_seats     INT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);


-- 3. Seats Table (Granular Inventory with Enum Status)
CREATE TABLE IF NOT EXISTS seats (
    id              BIGSERIAL PRIMARY KEY,
    show_id         BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number     VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- 'AVAILABLE', 'HELD', 'CONFIRMED'
    version         BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Unique constraint: A seat_number can only exist once per show
CREATE UNIQUE INDEX IF NOT EXISTS uq_seats_show_seat ON seats(show_id, seat_number);

-- Performance & Join Indexes
CREATE INDEX IF NOT EXISTS idx_seats_show_id ON seats(show_id);
CREATE INDEX IF NOT EXISTS idx_seats_show_status ON seats(show_id, status);
