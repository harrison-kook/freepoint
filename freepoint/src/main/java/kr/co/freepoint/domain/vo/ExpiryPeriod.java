package kr.co.freepoint.domain.vo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record ExpiryPeriod(int days) {

    public ExpiryPeriod {
        if (days < 0) {
            throw new IllegalArgumentException("만료일수는 음수일 수 없습니다: " + days);
        }
    }

    public static ExpiryPeriod ofDays(int days) {
        return new ExpiryPeriod(days);
    }

    public Instant expiresAtFrom(Instant from) {
        return from.plus(days, ChronoUnit.DAYS);
    }
}
