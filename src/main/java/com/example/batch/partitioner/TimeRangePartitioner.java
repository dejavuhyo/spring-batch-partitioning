package com.example.batch.partitioner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class TimeRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // 1. 처리해야 할 전체 데이터 개수 조회
        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE processed = false", Long.class
        );

        if (totalCount == null || totalCount == 0) {
            log.info(">>> [TimePartitioner] 처리할 변경 데이터가 없습니다.");
            return new HashMap<>();
        }

        // 2. NTILE을 사용하여 데이터를 균등하게 분할한 시간 범위(min/max)를 추출
        // 각 파티션이 거의 동일한 개수의 데이터를 가지도록 보장함
        String sql = 
            "WITH RankedData AS ( " +
            "    SELECT updated_at, NTILE(?) OVER (ORDER BY updated_at ASC, id ASC) as tile " +
            "    FROM customer WHERE processed = false " +
            ") " +
            "SELECT tile, MIN(updated_at) as min_t, MAX(updated_at) as max_t " +
            "FROM RankedData GROUP BY tile ORDER BY tile";

        return jdbcTemplate.query(sql, ps -> ps.setInt(1, gridSize), rs -> {
            Map<String, ExecutionContext> result = new HashMap<>();
            LocalDateTime lastMax = null;
            
            while (rs.next()) {
                int tile = rs.getInt("tile");
                LocalDateTime minT = rs.getTimestamp("min_t").toLocalDateTime();
                LocalDateTime maxT = rs.getTimestamp("max_t").toLocalDateTime();

                ExecutionContext context = new ExecutionContext();
                
                // 파티션 간의 빈틈을 없애기 위해 이전 파티션의 max를 현재의 min으로 사용 (첫 파티션 제외)
                if (lastMax != null && minT.isBefore(lastMax)) {
                     context.putString("minTime", lastMax.toString());
                } else {
                     context.putString("minTime", minT.toString());
                }
                
                // Reader 조건이 < maxTime 이므로, 실제 데이터인 maxT를 포함하기 위해 1ms 추가
                LocalDateTime currentMax = maxT.plusNanos(1000000); // 1ms
                context.putString("maxTime", currentMax.toString());
                lastMax = currentMax;

                result.put("partition" + (tile - 1), context);
                log.info(">>> [TimePartitioner] Partition {} : minTime={}, maxTime={}", 
                        tile-1, context.getString("minTime"), context.getString("maxTime"));
            }
            return result;
        });
    }
}
