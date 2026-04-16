# Spring Batch Partitioning

## 1. 설명
Java 21의 가상 스레드(Virtual Threads)와 Spring Batch의 파티셔닝(Partitioning)을 결합하여, 대량의 데이터를 저비용, 고효율로 처리하는 것을 목표로 하는 배치 애플리케이션이다.

## 2. 개발환경

* OpenJDK 21

* spring-boot 3.4.0

* mybatis-spring-boot-starter 3.0.4

* lombok 1.18.38

* PostgerSQL 17.9

* Gradle 9.4.1

## 3. 주요 특징

* 가상 스레드(Virtual Thread) 기반 병렬 처리: 플랫폼 스레드 대비 오버헤드가 적은 가상 스레드를 활용하여 20개 이상의 파티션을 동시에 병렬로 처리한다.

* 균등 데이터 분할 (NTILE Partitioner): SQL `NTILE` 함수를 사용하여 실제 데이터 개수(Count)를 기준으로 파티션을 균등하게 분할, 특정 파티션에 작업이 몰리는 병목 현상을 방지한다.

* 타임스탬프 기반 변경분 추출: `updated_at` 컬럼과 `processed` 플래그를 활용하여 변경되거나 처리되지 않은 데이터만 정밀하게 타겟팅하여 처리한다.

* 데드락 방지 설계: 파티션 간 경계 조건을 배타적으로 설정(`>=`, `<`)하고, 일관된 정렬 순서(`updated_at`, `id`)를 보장하여 DB 데드락 위험을 최소화했다.

* 안정적인 스케줄링: `fixedDelay = 10000`(10초) 설정을 통해 이전 작업 완료 후 대기 시간을 보장하며, 중복 실행을 완벽히 차단한다.

## 4. 아키텍처

* Scheduler: `CustomerJobScheduler`가 10초 주기로 배치를 트리거한다.

* Manager Step: `TimeRangePartitioner`가 전체 처리 대상을 20개의 파티션으로 분할한다.

* TaskExecutor: 가상 스레드 기반의 `VirtualThreadTaskExecutor`가 각 파티션을 독립적인 가상 스레드에서 실행한다.

* Worker Step: 각 파티션은 할당된 시간 범위 내의 데이터를 1,000건 단위(`Chunk`)로 읽어 상태를 업데이트한다.

## 5. 설정 및 실행 방법

### 1) Database 설정 (PostgreSQL)
`database/table.sql` 스크립트를 실행하여 필요한 테이블, 인덱스, 트리거를 생성한다.

### 2) 환경 설정 (application.yml)
`src/main/resources/application.yml`에서 데이터베이스 연결 정보를 수정한다.

## 6. 성능 및 최적화 전략

* 병렬성 극대화: `gridSize(20)`와 가상 스레드를 조합하여 I/O 대기 시간을 최소화하고 CPU 자원을 효율적으로 사용한다.

* I/O 효율화: `chunkSize` 및 `pageSize`를 1,000으로 DB 통신 횟수를 최적화했다.

* 인덱스 활용: `(processed, updated_at, id)` 복합 인덱스를 설계하여 100만 건 이상의 대량 데이터 환경에서도 파티셔닝 쿼리가 Full Table Scan 없이 인덱스 스캔만으로 동작한다.

* 커넥션 풀 최적화: 높은 병렬성을 수용하기 위해 HikariCP `maximum-pool-size`를 30으로 확장했다.
