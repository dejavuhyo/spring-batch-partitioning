-- Create table
CREATE TABLE customer
(
    id         BIGSERIAL PRIMARY KEY,   -- 자동 증가 PK (파티셔닝 기준)
    name       VARCHAR(100) NOT NULL,
    processed  BOOLEAN   DEFAULT FALSE, -- 처리 여부 플래그
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert data
INSERT INTO customer (name, processed)
SELECT 'Customer_' || i,
       false
FROM generate_series(1, 100000) s(i)
;
