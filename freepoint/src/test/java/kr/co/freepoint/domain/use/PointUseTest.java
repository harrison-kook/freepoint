package kr.co.freepoint.domain.use;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.EarnStatus;
import kr.co.freepoint.domain.account.EarnType;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointEarn;
import kr.co.freepoint.domain.vo.ExpiryPeriod;
import kr.co.freepoint.domain.vo.OrderNo;
import kr.co.freepoint.domain.vo.PointAmount;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static kr.co.freepoint.testsupport.PointPolicyFixtures.defaultPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointUseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * TC-USE-001
     * 주문번호와 함께 사용하면 사용 이력에 주문번호가 기록된다
     * Given : 계정 잔액 1000
     * When : orderNo="A1234", amount=500 사용
     * Then : PointUse 생성, orderNo="A1234" 기록, pointKey 발급
     */
    @Test
    void TcUse001() {
        PointAccount account = PointAccount.create("user-1");
        account.earn(PointAmount.of(1000), EarnType.NORMAL, null, defaultPolicy(), NOW);

        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(500), NOW);

        assertThat(use.pointKey()).isNotNull();
        assertThat(use.orderNo()).isEqualTo(new OrderNo("A1234"));
        assertThat(use.amount()).isEqualTo(PointAmount.of(500));
    }

    /**
     * TC-USE-002
     * 보유 잔액보다 큰 금액을 사용하려 하면 실패한다
     * Given : 계정 잔액 500
     * When : amount=600 사용 시도
     * Then : 예외 발생(잔액부족), 상태 변화 없음
     */
    @Test
    void TcUse002() {
        PointAccount account = PointAccount.create("user-1");
        account.earn(PointAmount.of(500), EarnType.NORMAL, null, defaultPolicy(), NOW);

        assertThatThrownBy(() -> account.use(new OrderNo("A1234"), PointAmount.of(600), NOW))
                .isInstanceOf(PointException.class);
        assertThat(account.totalBalance(NOW)).isEqualTo(PointAmount.of(500));
    }

    /**
     * TC-USE-003
     * 여러 적립건에 걸쳐 소진될 경우, 적립건별 소진 금액이 1원 단위로 정확히 기록된다
     * Given : A(1000, 만료 늦음), B(500, 만료 늦음) 순서로 적립
     * When : orderNo="A1234", amount=1200 사용
     * Then : allocation: A 1000 전액 + B 200, A.remaining=0, B.remaining=300
     */
    @Test
    void TcUse003() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn a = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(100), defaultPolicy(), NOW);
        PointEarn b = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(100), defaultPolicy(), NOW);

        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(1200), NOW);

        List<PointUseAllocationLine> allocations = use.allocations();
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).earnPointKey()).isEqualTo(a.pointKey());
        assertThat(allocations.get(0).allocatedAmount()).isEqualTo(PointAmount.of(1000));
        assertThat(allocations.get(1).earnPointKey()).isEqualTo(b.pointKey());
        assertThat(allocations.get(1).allocatedAmount()).isEqualTo(PointAmount.of(200));
        assertThat(a.remainingAmount()).isEqualTo(PointAmount.zero());
        assertThat(b.remainingAmount()).isEqualTo(PointAmount.of(300));
    }

    /**
     * TC-USE-004
     * 관리자 수기지급 적립이 있으면 일반 적립보다 먼저 소진된다
     * Given : 일반적립 A(1000, 만료 짧음), 관리자지급 M(500, 만료 김)
     * When : amount=300 사용
     * Then : M에서 우선 소진 (M.remaining=200, A.remaining=1000 그대로)
     */
    @Test
    void TcUse004() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn normal = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(10), defaultPolicy(), NOW);
        PointEarn manual = account.earn(PointAmount.of(500), EarnType.MANUAL_ADMIN, ExpiryPeriod.ofDays(300), defaultPolicy(), NOW);

        account.use(new OrderNo("A1234"), PointAmount.of(300), NOW);

        assertThat(manual.remainingAmount()).isEqualTo(PointAmount.of(200));
        assertThat(normal.remainingAmount()).isEqualTo(PointAmount.of(1000));
    }

    /**
     * TC-USE-005
     * 동일 우선순위 그룹 내에서는 만료일이 짧게 남은 적립부터 소진된다
     * Given : 일반적립 A(만료 10일 뒤), B(만료 3일 뒤), 둘 다 500원
     * When : amount=500 사용
     * Then : B가 먼저 전액 소진, A는 그대로
     */
    @Test
    void TcUse005() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn a = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(10), defaultPolicy(), NOW);
        PointEarn b = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(3), defaultPolicy(), NOW);

        account.use(new OrderNo("A1234"), PointAmount.of(500), NOW);

        assertThat(b.remainingAmount()).isEqualTo(PointAmount.zero());
        assertThat(a.remainingAmount()).isEqualTo(PointAmount.of(500));
    }

    /**
     * TC-USE-006
     * 이미 만료된 적립은 사용 대상에서 제외된다
     * Given : A(500, 이미 만료), B(500, 미만료)
     * When : amount=300 사용
     * Then : B에서만 소진, A는 소진 대상에서 제외
     */
    @Test
    void TcUse006() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn expired = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(1), defaultPolicy(), NOW);
        PointEarn active = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(100), defaultPolicy(), NOW);
        Instant afterExpiry = NOW.plus(2, ChronoUnit.DAYS);

        account.use(new OrderNo("A1234"), PointAmount.of(300), afterExpiry);

        assertThat(expired.remainingAmount()).isEqualTo(PointAmount.of(500));
        assertThat(active.remainingAmount()).isEqualTo(PointAmount.of(200));
    }

    // TC-USE-007
    /**
     * TC-USE-007
     * 소진되어 잔액이 0이 된 적립은 이후 소진 대상에서 제외된다
     * Given : A(500)
     * When : amount=500 사용 (전액 소진)
     * Then : A.remainingAmount=0, A.status=EXHAUSTED, 이후 사용 요청 시 A 후보에서 제외
     */
    @Test
    void TcUse007() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn a = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(10), defaultPolicy(), NOW);

        account.use(new OrderNo("A1234"), PointAmount.of(500), NOW);

        assertThat(a.remainingAmount()).isEqualTo(PointAmount.zero());
        assertThat(a.status()).isEqualTo(EarnStatus.EXHAUSTED);

        PointEarn b = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(10), defaultPolicy(), NOW);
        account.use(new OrderNo("A1235"), PointAmount.of(100), NOW);

        assertThat(a.remainingAmount()).isEqualTo(PointAmount.zero());
        assertThat(b.remainingAmount()).isEqualTo(PointAmount.of(400));
    }
}
