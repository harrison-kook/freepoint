package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;
import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.policy.PointPolicyRepository;
import kr.co.freepoint.domain.use.Allocation;
import kr.co.freepoint.domain.use.PointUse;
import kr.co.freepoint.domain.use.PointUseRepository;
import kr.co.freepoint.domain.vo.PointAmount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class CancelPointUseService {

    private final PointUseRepository useRepository;
    private final PointAccountRepository accountRepository;
    private final PointPolicyRepository policyRepository;
    private final Clock clock;

    public CancelPointUseService(PointUseRepository useRepository, PointAccountRepository accountRepository,
                                 PointPolicyRepository policyRepository, Clock clock) {
        this.useRepository = useRepository;
        this.accountRepository = accountRepository;
        this.policyRepository = policyRepository;
        this.clock = clock;
    }

    @Transactional
    public PointUseCancelResult cancel(String usePointKeyValue, long amount) {
        PointUse use = useRepository.findByPointKey(usePointKeyValue)
                .orElseThrow(() -> new PointException("USE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "사용 내역을 찾을 수 없습니다: " + usePointKeyValue));

        List<Allocation> plan = use.planCancel(PointAmount.of(amount));
        useRepository.save(use);

        if (!plan.isEmpty()) {
            PointAccount account = accountRepository.findByEarnPointKeyForUpdate(plan.get(0).earnPointKey().value())
                    .orElseThrow(() -> new PointException("ACCOUNT_NOT_FOUND", HttpStatus.INTERNAL_SERVER_ERROR,
                            "계정을 찾을 수 없습니다."));
            PointPolicy policy = policyRepository.findCurrent()
                    .orElseThrow(() -> new PointException("POLICY_NOT_FOUND", HttpStatus.INTERNAL_SERVER_ERROR,
                            "포인트 정책이 설정되지 않았습니다."));

            account.restore(plan, policy, clock.instant());
            accountRepository.save(account);
        }

        return PointUseCancelResult.from(use);
    }
}
