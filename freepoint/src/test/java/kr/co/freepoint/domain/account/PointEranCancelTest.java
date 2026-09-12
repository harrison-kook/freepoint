package kr.co.freepoint.domain.account;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.vo.PointAmount;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static kr.co.freepoint.testsupport.PointPolicyFixtures.defaultPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PointEranCancelTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * TC-EARNCANCEL-001
     * 전혀 사용되지 않은 적립은 전액 취소할 수 있다
     * Given : pointKey=A 로 1000원 적립, 미사용
     * When : A 적립취소
     * Then : A.remainingAmount=0, A.status=CANCELED, 계정 잔액 -1000
     */
    @Test
    void TcEarnCancel001() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn earn = account.earn(PointAmount.of(1000), EarnType.NORMAL, null, defaultPolicy(), NOW);

        account.cancelEarn(earn.pointKey());

        assertThat(earn.remainingAmount()).isEqualTo(PointAmount.zero());
        assertThat(earn.status()).isEqualTo(EarnStatus.CANCELED);
        assertThat(account.totalBalance(NOW)).isEqualTo(PointAmount.zero());
    }

    /**
     * TC-EARNCANCEL-002
     * 일부라도 사용된 적립은 취소할 수 없다
     * Given : A(1000원 적립) 중 300원 사용됨 (remaining=700)
     * When : A 적립취소 시도
     * Then : 예외 발생, A 상태 변화 없음
     */
    @Test
    void TcEarnCancel002() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn earn = account.earn(PointAmount.of(1000), EarnType.NORMAL, null, defaultPolicy(), NOW);
        earn.consume(PointAmount.of(300));

        assertThatThrownBy(() -> account.cancelEarn(earn.pointKey()))
                .isInstanceOf(PointException.class);
        assertThat(earn.remainingAmount()).isEqualTo(PointAmount.of(700));
        assertThat(earn.status()).isEqualTo(EarnStatus.ACTIVE);
    }

    /**
     * TC-EARNCANCEL-003
     * 이미 취소된 적립을 다시 취소하면 실패한다
     * Given : A 적립 후 이미 취소됨
     * When : A 재취소 시도
     * Then : 예외 발생
     */
    @Test
    void TcEarnCancel003() {
        PointAccount account = PointAccount.create("user-1");
        PointEarn earn = account.earn(PointAmount.of(1000), EarnType.NORMAL, null, defaultPolicy(), NOW);
        account.cancelEarn(earn.pointKey());

        assertThatThrownBy(() -> account.cancelEarn(earn.pointKey()))
                .isInstanceOf(PointException.class);
    }
}
