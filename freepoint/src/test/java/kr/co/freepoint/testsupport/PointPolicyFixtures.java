package kr.co.freepoint.testsupport;

import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.policy.PointPolicyRepository;

public class PointPolicyFixtures {

    private static final long MAX_EARN_AMOUNT = 100_000;
    private static final long MAX_BALANCE_AMOUNT = 1_000_000;
    private static final int MIN_EXPIRE_DAYS = 1;
    private static final int MAX_EXPIRE_DAYS = 1825;
    private static final int DEFAULT_EXPIRE_DAYS = 365;

    private PointPolicyFixtures() {
    }

    public static PointPolicy defaultPolicy() {
        return new PointPolicy(MAX_EARN_AMOUNT, MAX_BALANCE_AMOUNT, MIN_EXPIRE_DAYS, MAX_EXPIRE_DAYS, DEFAULT_EXPIRE_DAYS);
    }

    // @SpringBootTest 메서드끼리 같은 컨텍스트(H2 DB)를 공유해 정책 행이 여러 개 생기면 findCurrent()가 불안정해지므로,
    // 항상 기존 행을 재사용해 기본값으로 리셋한다(매번 새로 insert하지 않음).
    public static void resetToDefault(PointPolicyRepository repository) {
        PointPolicy policy = repository.findCurrent().orElseGet(PointPolicyFixtures::defaultPolicy);
        policy.update(MAX_EARN_AMOUNT, MAX_BALANCE_AMOUNT, MIN_EXPIRE_DAYS, MAX_EXPIRE_DAYS, DEFAULT_EXPIRE_DAYS);
        repository.save(policy);
    }
}
