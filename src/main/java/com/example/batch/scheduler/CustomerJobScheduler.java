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

    // fixedDelay: 이전 작업이 종료된 시점부터 설정된 시간 후에 다시 실행
    @Scheduled(fixedDelay = 10000) // 10초(10000ms) 대기
    public void runCustomerJob() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            log.info(">>> 배치 실행 시작...");
            jobLauncher.run(customerJob, params);
            log.info(">>> 배치 실행 완료 (10초 대기 후 재실행).");

        } catch (Exception e) {
            // 종료 중 발생하는 인터럽트 예외는 무시하거나 간결하게 로그 남김
            if (isShutdownException(e)) {
                log.info(">>> 애플리케이션 종료 중 배치가 중단되었습니다.");
            } else {
                log.error("배치 실행 중 에러 발생: {}", e.getMessage(), e);
            }
        }
    }

    private boolean isShutdownException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof InterruptedException) return true;
            if (cause.getMessage() != null && (
                cause.getMessage().contains("Closed by interrupt") || 
                cause.getMessage().contains("interrupted by signal")
            )) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
