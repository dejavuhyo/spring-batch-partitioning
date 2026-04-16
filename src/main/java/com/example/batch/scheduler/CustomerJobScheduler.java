package com.example.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job customerJob;

    // cron 식: 초 분 시 일 월 요일
    // "*/10 * * * * *" 는 10초마다 실행을 의미합니다.
    @Scheduled(cron = "*/10 * * * * *")
    public void runCustomerJob() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis()) // 중복 실행 방지를 위한 필수 파라미터
                    .toJobParameters();

            log.info(">>> 10초 주기 배치 시작...");
            jobLauncher.run(customerJob, params);

        } catch (Exception e) {
            log.error("배치 실행 중 에러 발생: {}", e.getMessage());
        }
    }
}
