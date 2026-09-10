package kr.co.freepoint.domain.vo;

public record PointAmount(long value) implements Comparable<PointAmount>{

    public PointAmount {
        if (value < 0) {
            throw new IllegalArgumentException("포인트 금액은 음수일 수 없습니다: " + value);
        }
    }

    public static PointAmount of(long value) {
        return new PointAmount(value);
    }

    public static PointAmount zero() {
        return new PointAmount(0);
    }

    public PointAmount add(PointAmount other) {
        return new PointAmount(this.value + other.value);
    }

    public PointAmount subtract(PointAmount other) {
        if (this.value < other.value) {
            throw new IllegalArgumentException("차감할 금액이 보유 금액보다 큽니다.");
        }
        return new PointAmount(this.value - other.value);
    }

    public boolean isLessThan(PointAmount other) {
        return this.value < other.value;
    }

    public boolean isGreaterThan(PointAmount other) {
        return this.value > other.value;
    }

    @Override
    public int compareTo(PointAmount other) {
        return Long.compare(this.value, other.value);
    }
}
