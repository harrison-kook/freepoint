package kr.co.freepoint.application;

import kr.co.freepoint.domain.policy.PointPolicy;

public record PointPolicyResult(long maxEarnAmount,
                                long maxBalanceAmount,
                                int minExpireDays,
                                int maxExpireDays,
                                int defaultExpireDays) {

    public static PointPolicyResult from(PointPolicy policy) {
        return new PointPolicyResult(
                policy.maxEarnAmount(),
                policy.maxBalanceAmount(),
                policy.minExpireDays(),
                policy.maxExpireDays(),
                policy.defaultExpireDays());
    }
}
