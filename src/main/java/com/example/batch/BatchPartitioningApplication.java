package com.example.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BatchPartitioningApplication {

    public static void main(String[] args) {
        org.springframework.context.ConfigurableApplicationContext context = SpringApplication.run(BatchPartitioningApplication.class, args);
        // 스케줄러가 동작할 수 있도록 유지하거나, 명시적으로 종료되지 않게 처리 (배치 앱은 보통 특정 조건 시 종료)
        // 하지만 @EnableScheduling이 있고 web-type: none이면 데몬 형태로 돌아가야 함.
    }

}
