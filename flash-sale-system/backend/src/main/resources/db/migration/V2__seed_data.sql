-- Seed initial admin user (password: admin123, BCrypt encoded)
INSERT INTO users (name, email, password_hash, role)
VALUES ('Administrator', 'admin@flashsale.local', '$2a$10$wK1F5y4Fvh8dE1B7uN25Z.m9k9h9R1fS3H6jL4gW5V1X9Z8m1Q9Y2', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- Seed a sample product for quick testing
INSERT INTO products (name, description, price, category, image_url)
VALUES ('Limited Edition Gaming Console', 'Next-gen 4K 120FPS gaming console with ultra-fast SSD', 49999.00, 'Gaming', 'https://placeholder.com/console.png')
ON CONFLICT DO NOTHING;
