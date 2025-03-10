CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id INT REFERENCES categories(id) ON DELETE SET NULL,
    UNIQUE (name, parent_id)
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    url TEXT NOT NULL UNIQUE,
    category_id INT NOT NULL REFERENCES categories(id) ON DELETE CASCADE
);

CREATE TABLE product_prices (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    date TIMESTAMP(0) NOT NULL,
    price INT NOT NULL
);

CREATE INDEX idx_product_prices_date ON product_prices (date);