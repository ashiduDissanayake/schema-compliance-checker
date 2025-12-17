-- Type Mismatch (amount is INT instead of DECIMAL)
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id INT PRIMARY KEY,
    user_id INT,
    amount INT,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE VIEW user_summary AS SELECT id, name FROM users;
