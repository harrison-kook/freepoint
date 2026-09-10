package kr.co.freepoint.application;

import kr.co.freepoint.domain.account.PointEarn;

import java.time.Instant;
import java.util.List;

public record PointEarnDetailResult(String pointKey,
                                    long amount,
                                    long remainingAmount,
                                    String earnType,
                                    String status,
                                    Instant earnedAt,
                                    Instant expiresAt,
                                    List<UsageDetail> usages) {

    public record UsageDetail(String orderNo, long amount) {
    }

    public static PointEarnDetailResult from(PointEarn earn, List<UsageDetail> usages) {
        return new PointEarnDetailResult(
                earn.pointKey().value(),
                earn.amount().value(),
                earn.remainingAmount().value(),
                earn.earnType().name(),
                earn.status().name(),
                earn.earnedAt(),
                earn.expiresAt(),
                usages);
    }
}

