package kr.co.freepoint.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.co.freepoint.domain.account.EarnType;

public record EarnRequest(
        @NotBlank(message = "userId는 비어있을 수 없습니다.") String userId,
        long amount,
        @NotNull(message = "earnType은 필수입니다.") EarnType earnType,
        Integer expireDays) {
}

