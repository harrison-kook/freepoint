package kr.co.freepoint.adapter.web;

import jakarta.validation.constraints.NotBlank;

public record UseRequest(
        @NotBlank(message = "userId는 비어있을 수 없습니다.") String userId,
        @NotBlank(message = "orderNo는 비어있을 수 없습니다.") String orderNo,
        long amount) {
}
