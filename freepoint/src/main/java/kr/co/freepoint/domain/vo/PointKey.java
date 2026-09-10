package kr.co.freepoint.domain.vo;

import java.util.UUID;

public record PointKey(String value) {

    public PointKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("pointKey는 비어있을 수 없습니다.");
        }
    }

    public static PointKey newKey() {
        return new PointKey(UUID.randomUUID().toString());
    }
}
