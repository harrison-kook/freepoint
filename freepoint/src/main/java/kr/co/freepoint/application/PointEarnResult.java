package kr.co.freepoint.application;

import kr.co.freepoint.domain.account.EarnType;
import kr.co.freepoint.domain.account.PointEarn;

import java.time.Instant;

public record PointEarnResult(String pointKey, long amount, EarnType earnType, Instant earnedAt, Instant expiresAt) {

    public static PointEarnResult from(PointEarn earn) {
        return new PointEarnResult(
                earn.pointKey().value(),
                earn.amount().value(),
                earn.earnType(),
                earn.earnedAt(),
                earn.expiresAt());
    }
}
