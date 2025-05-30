package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {


    @InjectMocks    // userPointTable, pointHistoryTable 컴포넌트를 pointService에 주입
    PointService pointService;

    @Mock
    UserPointTable userPointTable;

    @Mock
    PointHistoryTable pointHistoryTable;

    private final long userId = 1L;
    private final long amount = 1000L;
    private final long invalidAmount = -1000L;
    private final long updateMillis = System.currentTimeMillis();
    private final long pointHistoryId = 1L;
    private final long maxPoint = 2_000_000L;

    private UserPoint createUserPoint(long point) {
        return new UserPoint(userId, point, updateMillis);
    }

    private PointHistory createPointHistory(long id, TransactionType type) {
        return new PointHistory(id, userId, amount, type, updateMillis);
    }


    /**
     * # Method설명 : pointService.point() 테스트
     * # MethodName : 유저의_포인트_조회_성공
     **/
    @Test
    void 유저의_포인트_조회_성공() {

        // # given
        UserPoint userPoint = createUserPoint(amount);  // 예상 결과값
        when(userPointTable.selectById(userId)).thenReturn(userPoint);  // userPointTable의 selectById() 호출 시 예상 결과값(userPoint)을 리턴하도록 설정

        // # when
        UserPoint result = pointService.point(userId);  // 특정 유저의 포인트 조회

        // # then
        assertThat(result.id()).isEqualTo(userPoint.id());
        assertThat(result.point()).isEqualTo(userPoint.point());
        verify(userPointTable).selectById(userId);  // userPointTable.selectById(userId)가 실제로 호출되었는지 검증

    }

    /**
     * # Method설명 : pointService.history() 테스트
     * # MethodName : 유저의_포인트_내역_조회_성공
     **/
    @Test
    void 유저의_포인트_내역_조회_성공() {

        // # given
        List<PointHistory> historyList = List.of(
                createPointHistory(pointHistoryId, TransactionType.CHARGE),
                createPointHistory(pointHistoryId + 1, TransactionType.USE)
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(historyList);  // pointHistoryTable.selectAllByUserId() 호출 시 예상 결과값(historyList)을 리턴하도록 설정

        // # when
        List<PointHistory> result = pointService.history(userId);

        // # then
        assertThat(result).isEqualTo(historyList);
        assertThat(result.size()).isEqualTo(historyList.size());    // userId만 필터링하여 해당 유저의 포인트 내역 비교
        verify(pointHistoryTable).selectAllByUserId(userId);    // 실제 호출 검증
    }

    /**
     * # Method설명 : pointService.charge() 테스트 - 실패(충전 금액 0이하)
     * # MethodName : 충전금액_0이하_예외발생
     **/
    @Test
    void 충전금액_0이하_예외발생() {

        // # when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {   //  assertThrows로 IllegalArgumentException 발생 여부 체크
            pointService.charge(userId, invalidAmount);
        });

        // # then
        assertThat(exception.getMessage()).isEqualTo("충전 금액은 1원 이상이어야 합니다.");
    }

    /**
     * # Method설명 : pointService.charge() 테스트 - 실패(최대 잔고 초과)
     * # MethodName : 초대잔고를_초과하여_포인트를_충전_예외발생
     **/
    @Test
    void 초대잔고를_초과하여_포인트를_충전_예외발생() {

        // # given
        UserPoint userPoint = createUserPoint(maxPoint);    // 최대 잔고를 보유한 유저
        when(userPointTable.selectById(userId)).thenReturn(userPoint);  // userPointTable.selectById() 호출 시 예상 결과값(userPoint)을 리턴하도록 설정

        // # when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.charge(userId, amount);    // amount 포인트 충전
        });

        // # then
        assertThat(exception.getMessage()).isEqualTo("충전 시 최대 잔고(" + maxPoint + ")를 초과할 수 없습니다.");
    }

    /**
     * # Method설명 : pointService.charge() 테스트 - 성공
     * # MethodName : 포인트_충전_성공
     **/
    @Test
    void 포인트_충전_성공() {

        // # given
        UserPoint userPoint = createUserPoint(amount);  // userId로 조회 후 userPoint
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        UserPoint updatedUserPoint = createUserPoint(userPoint.point() + amount);   // amount 충전 후 userPoint
        when(userPointTable.insertOrUpdate(updatedUserPoint.id(), updatedUserPoint.point())).thenReturn(updatedUserPoint);

        // # when
        UserPoint result = pointService.charge(userId, amount);  // 포인트 충전 결과

        // # then
        assertThat(result.id()).isEqualTo(updatedUserPoint.id());
        assertThat(result.point()).isEqualTo(updatedUserPoint.point());

        // 호출 검증
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId, updatedUserPoint.point());
    }


    /**
     * # Method설명 : pointService.use() 테스트 - 실패(사용 금액 0이하)
     * # MethodName : 사용금액_0이하_예외발생
     **/
    @Test
    void 사용금액_0이하_예외발생() {

        // # when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.use(userId, invalidAmount);    // -1000 포인트 사용
        });

        // # then
        assertThat(exception.getMessage()).isEqualTo("사용 포인트는 1원 이상이어야 합니다.");

    }

    /**
     * # Method설명 : pointService.use() 테스트 - 실패(잔고 부족)
     * # MethodName : 잔고보다_많은_포인트_사용_예외발생
     **/
    @Test
    void 잔고보다_많은_포인트_사용_예외발생() {

        // # given
        UserPoint userPoint = createUserPoint(amount);  // userId로 조회 후 userPoint
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        // # when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.use(userId, maxPoint); // 최대 잔고액만큼 포인트 사용
        });

        // # then
        assertThat(exception.getMessage()).isEqualTo("잔고가 부족해 포인트를 사용할 수 없습니다.");
        assertThat(userPoint.point()).isLessThan(maxPoint); // 현재 포인트와 사용 포인트 비교 검증
    }

    /**
     * # Method설명 : pointService.use() 테스트 - 성공
     * # MethodName : 포인트_사용_성공
     **/
    @Test
    void 포인트_사용_성공() {

        // # given
        UserPoint userPoint = createUserPoint(amount);  // userId로 조회 후 userPoint
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        long newPoint = userPoint.point() - amount;
        UserPoint updatedUserPoint = createUserPoint(newPoint); // amount 사용 후 userPoint
        when(userPointTable.insertOrUpdate(userPoint.id(), newPoint)).thenReturn(updatedUserPoint);

        // # when
        UserPoint result = pointService.use(userId, amount);

        assertThat(result.id()).isEqualTo(updatedUserPoint.id());
        assertThat(result.point()).isEqualTo(updatedUserPoint.point());

        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId, updatedUserPoint.point());
    }
}
