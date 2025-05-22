package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)    // 각 테스트 메서드 실행 후 컨텍스트 초기화
public class PointServiceConcurrencyTest {

    @Autowired
    private PointService pointService;

    /**
     * # Method설명 : 동시에 여러번 pointService.charge() 테스트 -> 순차적으로 포인트를 충전하고 누적된 포인트 검증
     * # MethodName : 동시에여러번포인트충천_성공
     **/
    @Test
    void 동시에여러번포인트충천_성공() throws InterruptedException{
        // # given
        long userId = 1L;              // 테스트할 유저 ID
        int threadCount = 10;          // 동시에 충전할 스레드 수
        long chargeAmount = 1_000L;    // 각 스레드가 충전할 금액

        // 고정된 개수의 스레드 풀 생성 (10개 스레드로 동시에 실행)
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch latch = new CountDownLatch(threadCount); // 모든 스레드가 종료될 때까지 대기하기 위한 동기화 도구

        // # when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.charge(userId, chargeAmount); // 각 스레드가 포인트를 충전
                } finally {
                    latch.countDown(); // 하나의 작업 끝
                }
            });
        }

        latch.await();        // 모든 스레드가 작업을 마칠 때까지 대기 (무한대기 방지를 위해 timeout 설정)
        executor.shutdown();  // 스레드풀 종료

        // # then
        UserPoint result = pointService.point(userId); // 최종 포인트 조회
        assertThat(result.point()).isEqualTo(chargeAmount * threadCount); // 누적 금액 검증

        List<PointHistory> history = pointService.history(userId); // 포인트 충전 내역 조회
        assertThat(history).hasSize(threadCount); // 포인트 충전 내역 개수랑 스레드 수 비교
    }

    /**
     * # Method설명 : 동시에 여러 스레드가 포인트 충전을 시도할 때,
     *               최대 잔고 제한을 초과하는 경우 일부는 성공하고 일부는 실패하는 상황을 테스트
     *               예외 발생 없이 성공한 충전 횟수, 최종 잔고, 충전 내역을 검증
     *               성공한 충전의 총액이 최대 잔고 제한을 넘지 않아야 함
     * # MethodName : 동시에여러번_최대잔고를초과하는포인트충전_일부성공_일부실패
     */
    @Test
    void 동시에여러번_최대잔고를초과하는포인트충전_일부성공_일부실패() throws InterruptedException {
        // # given
        long userId = 1L;              // 테스트할 유저 ID
        int threadCount = 10;          // 동시에 충전할 스레드 수
        long chargeAmount = 1_000_000L;    // 각 스레드가 충전할 금액
        long maxPoint = 2_000_000L;        // 최대 잔고 제한

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();   // 성공한 스레드 수 누적

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.charge(userId, chargeAmount);    // 포인트 충전
                    successCount.incrementAndGet(); // 성공한 경우만 카운트
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);  // 스레드 종료까지 대기
        executor.shutdown();

        int success = successCount.get();
        long expected = chargeAmount * success;   // 예상 포인트

        UserPoint result = pointService.point(userId);  // 포인트 충전후 잔고 조회
        assertThat(result.point()).isEqualTo(expected); // 예상 포인트 검증

        List<PointHistory> history = pointService.history(userId);  // 사용 내역
        assertThat(history).hasSize(success); // 성공한 충전 기록
        assertThat(success * chargeAmount).isLessThanOrEqualTo(maxPoint);   // 성공한 충전 총액이 최대 잔고 제한을 넘지 않아야 함
    }


    /**
     * # Method설명 : 동시에 여러번 pointService.use() 테스트 -> 순차적으로 포인트를 사용하고 누적된 포인트 검증
     * # MethodName : 동시에여러번포인트사용_성공
     **/
    @Test
    void 동시에여러번포인트사용_성공() throws InterruptedException{
        // # given
        long userId = 1L;              // 테스트할 유저 ID
        int threadCount = 10;          // 동시에 사용할 스레드 수
        long useAmount = 1_000L;       // 각 스레드가 사용할 금액
        long chargeAmount = 10_000L;    // 포인트 사용 전 충전할 금액

        pointService.charge(userId, chargeAmount);   // 포인트 충전

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);   // 고정된 개수의 스레드 풀 생성 (10개 스레드로 동시에 실행)
        CountDownLatch latch = new CountDownLatch(threadCount); // 모든 스레드가 종료될 때까지 대기하기 위한 동기화 도구

        // # when
        for(int i=0;i<threadCount;i++){
            executor.submit(()->{
                try{
                    pointService.use(userId, useAmount);    // 각 스레드가 포인트 사용
                } finally {
                    latch.countDown();  // 하나의 작업 끝
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);   // 스레드 종료까지 대기 (무한대기 방지를 위해 timeout 설정)
        executor.shutdown();    // 스레드풀 종료

        // # then
        long expected = chargeAmount - (useAmount * threadCount);   // 예상 포인트

        UserPoint result = pointService.point(userId);  // 최종 포인트 조회
        assertThat(result.point()).isEqualTo(expected); // 예상 포인트와 실제 포인트 비교

        List<PointHistory> history = pointService.history(userId); // 포인트 사용 내역 조회
        assertThat(history).hasSize(threadCount + 1); // 충전 1번 + 포인트 사용 내역 개수랑 스레드 수 비교
    }


    /**
     * # Method설명 : 동시에 여러 개의 스레드가 포인트를 사용하려 할 때,
     *               잔액보다 많은 금액을 사용할 경우 일부는 성공하고 일부는 실패하는 상황을 테스트
     *               예외 발생 없이 성공한 수와 잔여 포인트, 사용 내역을 검증
     * # MethodName : 동시에여러번_잔액보다많은금액사용_일부성공_일부실패
     */
    @Test
    void 동시에여러번_잔액보다많은금액사용_일부성공_일부실패() throws InterruptedException {
        // # given
        long userId = 1L;              // 테스트할 유저 ID
        int threadCount = 10;          // 동시에 사용할 스레드 수
        long useAmount = 1_000L;       // 각 스레드가 사용할 금액
        long chargeAmount = 5_000L;    // 포인트 사용 전 충전할 금액

        pointService.charge(userId, chargeAmount);  // 포인트 충전

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();   // 성공한 스레드 수 누적

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.use(userId, useAmount);    // 포인트 사용
                    successCount.incrementAndGet(); // 성공한 경우만 카운트
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);  // 스레드 종료까지 대기
        executor.shutdown();

        int success = successCount.get();
        long expected = chargeAmount - (useAmount * success);   // 예상 포인트

        UserPoint result = pointService.point(userId);  // 포인트 사용 후 남은 포인트 조회
        assertThat(result.point()).isEqualTo(expected); // 예상 포인트 검증

        List<PointHistory> history = pointService.history(userId);  // 사용 내역
        assertThat(history).hasSize(success + 1); // 충전 1건 + 성공한 사용 기록
        assertThat(success).isLessThanOrEqualTo((int)(chargeAmount / useAmount)); // 성공 수가 초과되지 않았는지 확인
    }
}
