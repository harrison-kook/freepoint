package kr.co.freepoint.application;

import kr.co.freepoint.domain.use.PointUse;

public record PointUseCancelResult(String pointKey, long canceledAmount, String status) {

    public static PointUseCancelResult from(PointUse use) {
        return new PointUseCancelResult(
                use.pointKey().value(),
                use.canceledAmount().value(),
                use.status().name());
    }
}
