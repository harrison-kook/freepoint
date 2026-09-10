package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;
import kr.co.freepoint.domain.account.PointEarn;
import kr.co.freepoint.domain.use.PointUseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class PointQueryService {

    private final PointAccountRepository accountRepository;
    private final PointUseRepository useRepository;
    private final Clock clock;

    public PointQueryService(PointAccountRepository accountRepository, PointUseRepository useRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.useRepository = useRepository;
        this.clock = clock;
    }

    public BalanceResult balance(String userId) {
        PointAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "계정을 찾을 수 없습니다: " + userId));
        return new BalanceResult(userId, account.totalBalance(clock.instant()).value());
    }

    public PointEarnDetailResult earnDetail(String pointKeyValue) {
        PointAccount account = accountRepository.findByEarnPointKey(pointKeyValue)
                .orElseThrow(() -> new PointException("EARN_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "적립 내역을 찾을 수 없습니다: " + pointKeyValue));
        PointEarn earn = account.earns().stream()
                .filter(e -> e.pointKey().value().equals(pointKeyValue))
                .findFirst()
                .orElseThrow(() -> new PointException("EARN_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "적립 내역을 찾을 수 없습니다: " + pointKeyValue));

        List<PointEarnDetailResult.UsageDetail> usages = useRepository.findAllByAllocationEarnPointKey(pointKeyValue).stream()
                .flatMap(use -> use.allocations().stream()
                        .filter(line -> line.earnPointKey().value().equals(pointKeyValue))
                        .map(line -> new PointEarnDetailResult.UsageDetail(use.orderNo().value(), line.allocatedAmount().value())))
                .toList();

        return PointEarnDetailResult.from(earn, usages);
    }
}
