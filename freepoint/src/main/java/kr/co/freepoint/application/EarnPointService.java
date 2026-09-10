package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.EarnType;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;
import kr.co.freepoint.domain.account.PointEarn;
import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.policy.PointPolicyRepository;
import kr.co.freepoint.domain.vo.ExpiryPeriod;
import kr.co.freepoint.domain.vo.PointAmount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class EarnPointService {

    private final PointAccountRepository accountRepository;
    private final PointPolicyRepository policyRepository;
    private final Clock clock;

    public EarnPointService(PointAccountRepository accountRepository, PointPolicyRepository policyRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.policyRepository = policyRepository;
        this.clock = clock;
    }

    @Transactional
    public PointEarnResult earn(String userId, long amount, EarnType earnType, Integer expireDays) {
        PointAccount account = accountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> accountRepository.save(PointAccount.create(userId)));
        PointPolicy policy = policyRepository.findCurrent()
                .orElseThrow(() -> new PointException("POLICY_NOT_FOUND", HttpStatus.INTERNAL_SERVER_ERROR,
                        "포인트 정책이 설정되지 않았습니다."));

        ExpiryPeriod requestedExpiry = expireDays != null ? ExpiryPeriod.ofDays(expireDays) : null;
        PointEarn earn = account.earn(PointAmount.of(amount), earnType, requestedExpiry, policy, clock.instant());
        accountRepository.save(account);

        return PointEarnResult.from(earn);
    }
}
