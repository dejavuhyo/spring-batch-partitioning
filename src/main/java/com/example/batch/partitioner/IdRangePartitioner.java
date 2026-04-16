package com.example.batch.partitioner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class IdRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // 1. 현재 처리해야 할(false) 데이터의 ID 범위를 조회
        Map<String, Object> resultValues = jdbcTemplate.queryForMap(
                "SELECT MIN(id) as min_id, MAX(id) as max_id FROM customer WHERE processed = false"
        );

        // 데이터가 없는 경우 처리
        if (resultValues.get("min_id") == null || resultValues.get("max_id") == null) {
            log.info(">>> [Partitioner] 처리할 데이터가 없습니다.");
            return new HashMap<>();
        }

        long min = (long) resultValues.get("min_id");
        long max = (long) resultValues.get("max_id");

        // 2. 파티션당 처리할 데이터 크기 계산
        long targetSize = (max - min) / gridSize + 1;
        Map<String, ExecutionContext> result = new HashMap<>();

        long start = min;
        long end = start + targetSize - 1;

        // 3. gridSize만큼 범위 분할
        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();

            // 마지막 파티션은 max값까지 포함하도록 처리
            if (end >= max) {
                end = max;
            }

            context.putLong("minId", start);
            context.putLong("maxId", end);

            result.put("partition" + i, context);
            log.info(">>> [Partitioner] Partition {} : minId={}, maxId={}", i, start, end);

            start += targetSize;
            end += targetSize;

            if (start > max) break; // 계산된 시작값이 max를 넘으면 중단
        }

        return result;
    }
}
