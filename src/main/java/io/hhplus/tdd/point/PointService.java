package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    private static final long MAX_POINT = 2_000_000L;   // 최대 잔고


    /**
     * # Method설명 : UserPointTable, PointHistoryTable 주입
     * - Repository 생성을 고민했지만 이미 두 컴포넌트가 Repository 역할을 하고 있어 바로 주입
     * # MethodName : PointService
     **/
    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * # Method설명 : 사용자ID 별로 락 관리
     * # MethodName : getLockForUser
     **/
    private ReentrantLock getLockForUser(long userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    }


    /**
     * # Method설명 : 특정 유저의 포인트 조회
     * # MethodName : point
     **/
    public UserPoint point(long userId) {
        return userPointTable.selectById(userId);
    }


    /**
     * # Method설명 : 특정 유저의 포인트 충전/이용 내역 조회
     * # MethodName : history
     **/
    public List<PointHistory> history(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    /**
     * # Method설명 : 특정 유저의 포인트 충전
     * # MethodName : charge
     **/
    public UserPoint charge(long userId, long amount) {

        if(amount <= 0){
            throw new IllegalArgumentException("충전 금액은 1원 이상이어야 합니다.");
        }

        ReentrantLock lock = getLockForUser(userId);
        lock.lock();
        try {
            // 특정 유저의 포인트 조회
            UserPoint userPoint = userPointTable.selectById(userId);

            // 충전 포인트
            long newPoint = userPoint.point() + amount;

            if (newPoint > MAX_POINT) {
                throw new IllegalArgumentException("충전 시 최대 잔고(" + MAX_POINT + ")를 초과할 수 없습니다.");
            }

            // 포인트 충전
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);

            // 포인트 충전 내역 저장
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

            return updatedUserPoint;
        } finally {
            lock.unlock();
        }
    }

    /**
     * # Method설명 : 특정 유저의 포인트 사용
     * # MethodName : use
     **/
    public UserPoint use(long userId, long amount) {

        if(amount <= 0){
            throw new IllegalArgumentException("사용 포인트는 1원 이상이어야 합니다.");
        }

        ReentrantLock lock = getLockForUser(userId);
        lock.lock();

        try {
            // 특정 유저의 포인트 조회
            UserPoint userPoint = userPointTable.selectById(userId);
            if (userPoint.point() < amount) {     // 보유 포인트가 사용할 포인트보다 적은 경우 사용 불가능
                throw new IllegalArgumentException("잔고가 부족해 포인트를 사용할 수 없습니다.");
            }

            // 포인트 사용
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, userPoint.point() - amount);

            // 포인트 사용 내역 저장
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

            return updatedUserPoint;
        }finally {
            lock.unlock();
        }
    }
}
