package kr.co.freepoint.domain.vo;

public record OrderNo(String value) {

    public OrderNo {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("주문번호는 비어있을 수 없습니다.");
        }
    }
}
