CREATE TABLE inventory (
                           product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           name VARCHAR(255) NOT NULL,
                           category VARCHAR(100),
                           stock INT NOT NULL DEFAULT 0,
                           min_threshold INT,
                           last_updated TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO inventory (name, category, stock) VALUES
                                                  ('Laptop Pro', 'Electronics', 50),
                                                  ('Wireless Mouse', 'Accessories', 200),
                                                  ('Ergonomic Chair', 'Furniture', 30);