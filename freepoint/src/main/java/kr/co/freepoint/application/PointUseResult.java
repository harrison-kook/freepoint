package kr.co.freepoint.application;

import kr.co.freepoint.domain.use.PointUse;

import java.time.Instant;

public record PointUseResult(String pointKey, String orderNo, long amount, Instant usedAt) {

    public static PointUseResult from(PointUse use) {
        return new PointUseResult(
                use.pointKey().value(),
                use.orderNo().value(),
                use.amount().value(),
                use.usedAt());
    }
}
