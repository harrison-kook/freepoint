package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.EarnType;
import kr.co.freepoint.domain.policy.PointPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static kr.co.freepoint.testsupport.PointPolicyFixtures.defaultPolicy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EarnPointServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final FakePointAccountRepository accountRepository = new FakePointAccountRepository();
    private final FakePointPolicyRepository policyRepository = new FakePointPolicyRepository();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EarnPointService earnPointService = new EarnPointService(accountRepository, policyRepository, clock);

    @BeforeEach
    void setUp() {
        policyRepository.save(defaultPolicy());
    }

    /**
     * TC-EARN-014
     * 정책(1회한도/보유한도/만료일범위)을 변경하면 이후 적립 요청에 즉시 반영된다 (하드코딩 금지 검증)
     * Given : 정책.maxEarnAmount=100000
     * When : 정책을 maxEarnAmount=50000 으로 변경 후 amount=60000 적립 시도
     * Then : 예외 발생 (변경된 정책이 즉시 반영됨)
     */
    @Test
    void TcEarn0014() {
        PointPolicy current = policyRepository.findCurrent().orElseThrow();
        current.update(50_000, 1_000_000, 1, 1825, 365);
        policyRepository.save(current);

        assertThatThrownBy(() -> earnPointService.earn("user-1", 60_000, EarnType.NORMAL, null))
                .isInstanceOf(PointException.class);
    }
}