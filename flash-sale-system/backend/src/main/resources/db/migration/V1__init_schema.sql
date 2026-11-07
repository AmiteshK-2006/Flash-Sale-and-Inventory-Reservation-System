CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE flash_sales (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    sale_stock INT NOT NULL,
    max_quantity_per_purchase INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_balances (
    sale_id BIGINT PRIMARY KEY REFERENCES flash_sales(id),
    total_stock INT NOT NULL,
    reserved_stock INT NOT NULL DEFAULT 0,
    sold_stock INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_inventory_positive CHECK (total_stock >= 0 AND reserved_stock >= 0 AND sold_stock >= 0),
    CONSTRAINT check_inventory_conservation CHECK (reserved_stock + sold_stock <= total_stock)
);

CREATE TABLE reservations (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    sale_id BIGINT NOT NULL REFERENCES flash_sales(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_idempotency_reservation UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_reservations_expires_at ON reservations(expires_at) WHERE status = 'RESERVED';
CREATE INDEX idx_reservations_user_id ON reservations(user_id);
CREATE INDEX idx_reservations_sale_id ON reservations(sale_id);

CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    reservation_id VARCHAR(36) NOT NULL REFERENCES reservations(id),
    idempotency_key VARCHAR(255) NOT NULL,
    attempt_number INT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    mock_transaction_reference VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_reservation_payment_idempotency UNIQUE (reservation_id, idempotency_key)
);

CREATE TABLE orders (
    id VARCHAR(36) PRIMARY KEY,
    reservation_id VARCHAR(36) NOT NULL UNIQUE REFERENCES reservations(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    retry_count INT DEFAULT 0,
    last_error TEXT
);
CREATE INDEX idx_outbox_published_at ON outbox_events(published_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_records (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    operation_type VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(255),
    result_status VARCHAR(50),
    result_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT unique_user_idempotency_record UNIQUE (user_id, idempotency_key, operation_type)
);

CREATE TABLE simulation_runs (
    id VARCHAR(36) PRIMARY KEY,
    configuration JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_requests INT DEFAULT 0,
    successful_reservations INT DEFAULT 0,
    rejected_requests INT DEFAULT 0,
    failed_requests INT DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,
    throughput DECIMAL(10, 2) DEFAULT 0,
    latency_statistics JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);
