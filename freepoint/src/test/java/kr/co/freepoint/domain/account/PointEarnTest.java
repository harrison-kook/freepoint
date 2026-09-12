package kr.co.freepoint.domain.account;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.vo.ExpiryPeriod;
import kr.co.freepoint.domain.vo.PointAmount;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static kr.co.freepoint.testsupport.PointPolicyFixtures.defaultPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PointEarnTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * TC-EARN-001
     * 정상적으로 금액/만료일을 지정해 적립한다
     * Given : 계정 잔액 0
     * When : amount=1000, expireDays=30 로 적립
     * Then : pointKey 발급, remainingAmount=1000, expiresAt=적립일+30일, 계정 총 잔액 1000
     */
    @Test
    void TcEarn001() {
        PointAccount account = PointAccount.create("user-1");

        PointEarn earn = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(30), defaultPolicy(), NOW);

        assertThat(earn.pointKey()).isNotNull();
        assertThat(earn.remainingAmount()).isEqualTo(PointAmount.of(1000));
        assertThat(earn.expiresAt()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
        assertThat(account.totalBalance(NOW)).isEqualTo(PointAmount.of(1000));
    }

    /**
     * TC-EARN-002
     * 만료일을 지정하지 않으면 기본 365일이 적용된다
     * Given : 계정 잔액 0
     * When : amount=1000, expireDays 미지정
     * Then : expiresAt = 적립일 + 정책.defaultExpireDays(365)
     */
    @Test
    void TcEarn002() {
        PointAccount account = PointAccount.create("user-1");

        PointEarn earn = account.earn(PointAmount.of(1000), EarnType.NORMAL, null, defaultPolicy(), NOW);

        assertThat(earn.expiresAt()).isEqualTo(NOW.plus(365, ChronoUnit.DAYS));
    }

    /**
     * TC-EARN-003
     * 적립 금액이 1P 미만(0 또는 음수)이면 실패한다
     * Given : -
     * When : amount=0 으로 적립 시도
     * Then : InvalidPointAmountException (또는 동등 예외) 발생, 적립 미생성
     */
    @Test
    void TcEarn003() {
        PointAccount account = PointAccount.create("user-1");

        assertThatThrownBy(() -> account.earn(PointAmount.of(0), EarnType.NORMAL, null, defaultPolicy(), NOW))
                .isInstanceOf(PointException.class);
    }

    /**
     * TC-EARN-004
     * 적립 금액이 1P 미만(0 또는 음수)이면 실패한다
     * Given : -
     * When : amount=-100 으로 적립 시도
     * Then : 예외 발생
     */
    @Test
    void TcEarn004() {
        assertThatThrownBy(() -> PointAmount.of(-100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * TC-EARN-005
     * 적립 금액이 정책의 1회 최대 적립한도를 초과하면 실패한다
     * Given : 정책.maxEarnAmount=100000
     * When : amount=100001 로 적립 시도
     * Then : 예외 발생
     */
    @Test
    void TcEarn005() {
        PointAccount account = PointAccount.create("user-1");

        assertThatThrownBy(() -> account.earn(PointAmount.of(100_001), EarnType.NORMAL, null, defaultPolicy(), NOW))
                .isInstanceOf(PointException.class);
    }

    /**
     * TC-EARN-006
     * 적립 금액이 정책의 1회 최대 적립한도와 정확히 같으면 성공한다 (경계값)
     * Given : 정책.maxEarnAmount=100000
     * When : amount=100000 으로 적립
     * Then : 성공 (경계값)
     */
    @Test
    void TcEarn006() {
        PointAccount account = PointAccount.create("user-1");

        PointEarn earn = account.earn(PointAmount.of(100_000), EarnType.NORMAL, null, defaultPolicy(), NOW);

        assertThat(earn.amount()).isEqualTo(PointAmount.of(100_000));
    }

    /**
     * TC-EARN-007
     * 적립 후 총 보유액이 정책의 최대 보유한도를 초과하면 실패한다
     * Given : 계정 현재 잔액 999,000, 정책.maxBalance=1,000,000
     * When : amount=2000 적립 시도 (합계 1,001,000)
     * Then : 예외 발생, 적립 미생성
     */
    @Test
    void TcEarn007() {
        // 1회 적립 한도 제약과 분리해 보유한도 규칙만 검증하기 위해 1회 한도를 넉넉히 둔 정책 사용
        PointPolicy policy = new PointPolicy(2_000_000, 1_000_000, 1, 1825, 365);
        PointAccount account = PointAccount.create("user-1");
        account.earn(PointAmount.of(999_000), EarnType.NORMAL, null, policy, NOW);

        assertThatThrownBy(() -> account.earn(PointAmount.of(2000), EarnType.NORMAL, null, policy, NOW))
                .isInstanceOf(PointException.class);
        assertThat(account.totalBalance(NOW)).isEqualTo(PointAmount.of(999_000));
    }

    /**
     * TC-EARN-008
     * 적립 후 총 보유액이 정책의 최대 보유한도와 정확히 같으면 성공한다 (경계값)
     * Given : 계정 현재 잔액 999,000, 정책.maxBalance=1,000,000
     * When : amount=1000 적립 (합계 정확히 1,000,000)
     * Then : 성공 (경계값)
     */
    @Test
    void TcEarn008() {
        PointPolicy policy = new PointPolicy(2_000_000, 1_000_000, 1, 1825, 365);
        PointAccount account = PointAccount.create("user-1");
        account.earn(PointAmount.of(999_000), EarnType.NORMAL, null, policy, NOW);

        account.earn(PointAmount.of(1000), EarnType.NORMAL, null, policy, NOW);

        assertThat(account.totalBalance(NOW)).isEqualTo(PointAmount.of(1_000_000));
    }

    /**
     * TC-EARN-009
     * 만료일수가 1일 미만이면 실패, 정확히 1일이면 성공한다
     * Given : -
     * When : expireDays=0 으로 적립 시도
     * Then : 예외 발생
     */
    @Test
    void TcEarn009() {
        PointAccount account = PointAccount.create("user-1");

        assertThatThrownBy(() -> account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(0), defaultPolicy(), NOW))
                .isInstanceOf(PointException.class);
    }

    /**
     * TC-EARN-010
     * 만료일수가 1일 미만이면 실패, 정확히 1일이면 성공한다
     * Given : -
     * When : expireDays=1 로 적립
     * Then : 성공
     */
    @Test
    void TcEarn0010() {
        PointAccount account = PointAccount.create("user-1");

        PointEarn earn = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(1), defaultPolicy(), NOW);

        assertThat(earn.expiresAt()).isEqualTo(NOW.plus(1, ChronoUnit.DAYS));
    }

    /**
     * TC-EARN-011
     * 만료일수가 5년 이상이면 실패, 5년 미만 최댓값이면 성공한다
     * Given : -
     * When : expireDays=1825(5년) 로 적립 시도
     * Then : 예외 발생 (5년 미만이어야 함)
     */
    @Test
    void TcEarn0011() {
        PointAccount account = PointAccount.create("user-1");

        assertThatThrownBy(() -> account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(1825), defaultPolicy(), NOW))
                .isInstanceOf(PointException.class);
    }

    /**
     * TC-EARN-012
     * 만료일수가 5년 이상이면 실패, 5년 미만 최댓값이면 성공한다
     * Given : -
     * When : expireDays=1824 로 적립
     * Then : 성공
     */
    @Test
    void TcEarn0012() {
        PointAccount account = PointAccount.create("user-1");

        PointEarn earn = account.earn(PointAmount.of(1000), EarnType.NORMAL, ExpiryPeriod.ofDays(1824), defaultPolicy(), NOW);

        assertThat(earn.expiresAt()).isEqualTo(NOW.plus(1824, ChronoUnit.DAYS));
    }

    /**
     * TC-EARN-013
     * 관리자 수기 지급 적립은 일반 적립과 구분되는 타입으로 저장/조회된다
     * Given : -
     * When : 관리자 수기 지급으로 amount=1000 적립
     * Then : earnType=MANUAL_ADMIN 으로 저장됨, 일반 적립과 조회 시 구분됨
     */
    @Test
    void TcEarn0013() {
        PointAccount account = PointAccount.create("user-1");

        PointEarn manual = account.earn(PointAmount.of(1000), EarnType.MANUAL_ADMIN, null, defaultPolicy(), NOW);
        PointEarn normal = account.earn(PointAmount.of(1000), EarnType.NORMAL, null, defaultPolicy(), NOW);

        assertThat(manual.earnType()).isEqualTo(EarnType.MANUAL_ADMIN);
        assertThat(normal.earnType()).isEqualTo(EarnType.NORMAL);
    }
}
