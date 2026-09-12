package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelEarnServiceTest {

    private final FakePointAccountRepository accountRepository = new FakePointAccountRepository();
    private final CancelEarnService cancelEarnService = new CancelEarnService(accountRepository);

    /**
     * TC-EARNCANCEL-004
     * 존재하지 않는 pointKey로 취소를 시도하면 실패한다
     * Given : -
     * When : 존재하지 않는 pointKey로 취소 API 호출
     * Then : PointException 발생
     */
    @Test
    void TcCancelEarn004() {
        assertThatThrownBy(() -> cancelEarnService.cancel("no-such-point-key"))
                .isInstanceOf(PointException.class);
    }

}