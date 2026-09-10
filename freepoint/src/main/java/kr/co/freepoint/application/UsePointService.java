package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;
import kr.co.freepoint.domain.use.PointUse;
import kr.co.freepoint.domain.use.PointUseRepository;
import kr.co.freepoint.domain.vo.OrderNo;
import kr.co.freepoint.domain.vo.PointAmount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class UsePointService {

    private final PointAccountRepository accountRepository;
    private final PointUseRepository useRepository;
    private final Clock clock;

    public UsePointService(PointAccountRepository accountRepository, PointUseRepository useRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.useRepository = useRepository;
        this.clock = clock;
    }

    @Transactional
    public PointUseResult use(String userId, String orderNo, long amount) {
        PointAccount account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new PointException("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "계정을 찾을 수 없습니다: " + userId));

        PointUse use = account.use(new OrderNo(orderNo), PointAmount.of(amount), clock.instant());
        accountRepository.save(account);
        useRepository.save(use);

        return PointUseResult.from(use);
    }
}
