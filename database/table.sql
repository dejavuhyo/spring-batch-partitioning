-- Create table
CREATE TABLE customer
(
    id         BIGSERIAL PRIMARY KEY,   -- 자동 증가 PK (파티셔닝 기준)
    name       VARCHAR(100) NOT NULL,
    processed  BOOLEAN   DEFAULT FALSE, -- 처리 여부 플래그
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 데이터 수정 시각 추적
);

-- 업데이트 시 자동 갱신을 위한 함수 및 트리거 (PostgreSQL 예시)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = NOW();
   RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_customer_updated_at
BEFORE UPDATE ON customer
FOR EACH ROW
EXECUTE PROCEDURE update_updated_at_column();

-- 성능 최적화를 위한 인덱스 추가
CREATE INDEX idx_customer_processed_updated_at ON customer (processed, updated_at, id);

-- Insert data
INSERT INTO customer (name, processed, updated_at)
SELECT 'Customer_' || i,
       false,
       NOW() - (random() * interval '7 days') -- 최근 7일간의 랜덤한 수정 시각 시뮬레이션
FROM generate_series(1, 100000) s(i)
;
