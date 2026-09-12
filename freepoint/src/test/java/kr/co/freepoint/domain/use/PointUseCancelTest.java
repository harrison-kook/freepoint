package kr.co.freepoint.domain.use;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.EarnType;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointEarn;
import kr.co.freepoint.domain.policy.PointPolicy;
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

class PointUseCancelTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * TC-USECANCEL-001
     * 사용금액 전체를 취소하면 관련된 모든 적립건에 복원된다
     * Given : 사용 C: A에서 1000, B에서 200 소진 (총 1200)
     * When : C 전액(1200) 사용취소
     * Then : A.remaining +1000, B.remaining +200, C.canceledAmount=1200, C.status=FULLY_CANCELED
     */
    @Test
    void TcUseCancel001() {
        PointPolicy policy = defaultPolicy();
        PointAccount account = PointAccount.create("user-1");
        PointEarn a = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(100), policy, NOW);
        PointEarn b = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(100), policy, NOW);
        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(1200), NOW);

        List<Allocation> plan = use.planCancel(PointAmount.of(1200));
        account.restore(plan, policy, NOW);

        assertThat(a.remainingAmount()).isEqualTo(PointAmount.of(1000));
        assertThat(b.remainingAmount()).isEqualTo(PointAmount.of(500));
        assertThat(use.canceledAmount()).isEqualTo(PointAmount.of(1200));
        assertThat(use.status()).isEqualTo(PointUseStatus.FULLY_CANCELED);
    }

    /**
     * TC-USECANCEL-002
     * 사용금액 일부만 취소하면 소진했던 순서 그대로 앞에서부터 복원된다
     * Given : 사용 C: A에서 1000(seq1), B에서 200(seq2) 소진
     * When : C를 1100원 부분취소
     * Then : seq1(A)부터 최대치 복원: A +1000, 남은 100원은 seq2(B)에서 복원: B +100
     */
    @Test
    void TcUseCancel002() {
        PointPolicy policy = defaultPolicy();
        PointAccount account = PointAccount.create("user-1");
        PointEarn a = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(100), policy, NOW);
        PointEarn b = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(100), policy, NOW);
        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(1200), NOW);

        List<Allocation> plan = use.planCancel(PointAmount.of(1100));
        account.restore(plan, policy, NOW);

        assertThat(a.remainingAmount()).isEqualTo(PointAmount.of(1000));
        assertThat(b.remainingAmount()).isEqualTo(PointAmount.of(400));
        assertThat(use.canceledAmount()).isEqualTo(PointAmount.of(1100));
        assertThat(use.status()).isEqualTo(PointUseStatus.PARTIALLY_CANCELED);
    }

    /**
     * TC-USECANCEL-003
     * 취소 요청 금액이 (원 사용금액 - 기취소금액)을 초과하면 실패한다
     * Given : 사용 C=1200, 이미 300원 취소됨(canceledAmount=300)
     * When : 추가로 1000원 취소 시도 (누적 1300 > 1200)
     * Then : 예외 발생
     */
    @Test
    void TcUseCancel003() {
        PointPolicy policy = defaultPolicy();
        PointAccount account = PointAccount.create("user-1");
        account.earn(PointAmount.of(1200), EarnType.NORMAL, ExpiryPeriod.ofDays(100), policy, NOW);
        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(1200), NOW);
        account.restore(use.planCancel(PointAmount.of(300)), policy, NOW);

        assertThatThrownBy(() -> use.planCancel(PointAmount.of(1000)))
                .isInstanceOf(PointException.class);
        assertThat(use.canceledAmount()).isEqualTo(PointAmount.of(300));
    }

    /**
     * TC-USECANCEL-004
     * 복원 대상 적립건이 아직 만료되지 않았다면 해당 적립건의 잔액이 복원된다
     * Given : 사용 C가 B(미만료)에서 200 소진
     * When : 200원 사용취소
     * Then : B.remainingAmount +200 (신규 적립 생성 없음)
     */
    @Test
    void TcUseCancel004() {
        PointPolicy policy = defaultPolicy();
        PointAccount account = PointAccount.create("user-1");
        PointEarn b = account.earn(PointAmount.of(500), EarnType.NORMAL, ExpiryPeriod.ofDays(100), policy, NOW);
        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(200), NOW);
        int earnCountBefore = account.earns().size();

        List<Allocation> plan = use.planCancel(PointAmount.of(200));
        account.restore(plan, policy, NOW);

        assertThat(b.remainingAmount()).isEqualTo(PointAmount.of(500));
        assertThat(account.earns()).hasSize(earnCountBefore);
    }

    /**
     * TC-USECANCEL-005
     * 복원 대상 적립건이 이미 만료되었다면, 복원 대신 동일 금액의 신규 적립(새 pointKey)이 생성된다
     * Given : 사용 C가 A(이미 만료됨)에서 1000 소진
     * When : 1000원 사용취소
     * Then : A.remainingAmount은 변하지 않음(만료 상태 유지), 신규 PointEarn(E) 생성, E.amount=1000, E.remainingAmount=1000
     *
     * TC-USECANCEL-006
     * 신규 적립으로 복원된 건은 원래 적립과 구분되는 별도 타입(RESTORED_EXPIRED)을 가진다
     * Given : TC-USECANCEL-005 상황
     * When : 신규 적립 E 생성 후
     * Then : E.earnType=RESTORED_EXPIRED, E.pointKey는 A와 다른 새 값, E는 사용취소 이벤트를 참조(추적 가능)
     */
    @Test
    void TcUseCancel005() {
        PointPolicy policy = defaultPolicy();
        PointAccount account = PointAccount.create("user-1");
        PointEarn a = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(1), policy, NOW);
        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(1000), NOW);
        Instant afterExpiry = NOW.plus(2, ChronoUnit.DAYS);

        List<Allocation> plan = use.planCancel(PointAmount.of(1000));
        account.restore(plan, policy, afterExpiry);

        assertThat(a.remainingAmount()).isEqualTo(PointAmount.zero());

        PointEarn restored = account.earns().stream()
                .filter(earn -> earn.earnType() == EarnType.RESTORED_EXPIRED)
                .findFirst()
                .orElseThrow();
        assertThat(restored.amount()).isEqualTo(PointAmount.of(1000));
        assertThat(restored.remainingAmount()).isEqualTo(PointAmount.of(1000));
        assertThat(restored.pointKey()).isNotEqualTo(a.pointKey());
    }

    /**
     * TC-USECANCEL-007
     * 부분취소 후 남은 사용금액에 대해 추가로 부분취소할 수 있다 (누적 취소 검증)
     * Given : 사용 C=1200원, 1차 취소 500원 완료(canceledAmount=500)
     * When : 2차로 700원 취소 요청(누적 정확히 1200)
     * Then : 성공, C.status=FULLY_CANCELED
     */
    @Test
    void TcUseCancel007() {
        PointPolicy policy = defaultPolicy();
        PointAccount account = PointAccount.create("user-1");
        account.earn(PointAmount.of(1200), EarnType.NORMAL, ExpiryPeriod.ofDays(100), policy, NOW);
        PointUse use = account.use(new OrderNo("A1234"), PointAmount.of(1200), NOW);

        account.restore(use.planCancel(PointAmount.of(500)), policy, NOW);
        assertThat(use.status()).isEqualTo(PointUseStatus.PARTIALLY_CANCELED);

        account.restore(use.planCancel(PointAmount.of(700)), policy, NOW);

        assertThat(use.canceledAmount()).isEqualTo(PointAmount.of(1200));
        assertThat(use.status()).isEqualTo(PointUseStatus.FULLY_CANCELED);
    }
}
