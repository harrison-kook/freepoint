package kr.co.freepoint.application;

import kr.co.freepoint.domain.account.EarnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static kr.co.freepoint.testsupport.PointPolicyFixtures.defaultPolicy;
import static org.assertj.core.api.Assertions.assertThat;

class UsePointServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final FakePointAccountRepository accountRepository = new FakePointAccountRepository();
    private final FakePointPolicyRepository policyRepository = new FakePointPolicyRepository();
    private final FakePointUseRepository useRepository = new FakePointUseRepository();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EarnPointService earnPointService = new EarnPointService(accountRepository, policyRepository, clock);
    private final UsePointService usePointService = new UsePointService(accountRepository, useRepository, clock);

    @BeforeEach
    void setUp() {
        policyRepository.save(defaultPolicy());
    }

    /**
     * TC-USE-001
     * 주문번호와 함께 사용하면 사용_결과에 주문번호와 pointKey가 담긴다
     * 실행 : usePointService.use("user-1", "A1234", 500) 호출 (리포지토리 저장/조회 포함)
     * 검증대상 : 서비스가 반환하는 PointUseResult DTO의 pointKey/orderNo/amount
     * Given : 계정 잔액 1000
     * When : orderNo="A1234", amount=500 사용
     * Then : PointUse 생성, orderNo="A1234" 기록, pointKey 발급
     */
    @Test
    void TcUse001() {
        earnPointService.earn("user-1", 1000, EarnType.NORMAL, null);

        PointUseResult result = usePointService.use("user-1", "A1234", 500);

        assertThat(result.pointKey()).isNotNull();
        assertThat(result.orderNo()).isEqualTo("A1234");
        assertThat(result.amount()).isEqualTo(500);
    }
}
