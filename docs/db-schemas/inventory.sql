CREATE TABLE products (
                          product_id UUID PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          category VARCHAR(100)
);

CREATE TABLE inventory (
                           inventory_id BIGSERIAL PRIMARY KEY,
                           product_id UUID REFERENCES products,
                           quantity INT NOT NULL CHECK (quantity >= 0),
                           last_updated TIMESTAMPTZ DEFAULT NOW()
);