package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.policy.PointPolicyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPointPolicyService {

    private final PointPolicyRepository policyRepository;

    public AdminPointPolicyService(PointPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public PointPolicyResult get() {
        return PointPolicyResult.from(currentPolicy());
    }

    @Transactional
    public PointPolicyResult update(long maxEarnAmount, long maxBalanceAmount, int minExpireDays, int maxExpireDays, int defaultExpireDays) {
        PointPolicy policy = currentPolicy();
        policy.update(maxEarnAmount, maxBalanceAmount, minExpireDays, maxExpireDays, defaultExpireDays);
        policyRepository.save(policy);
        return PointPolicyResult.from(policy);
    }

    private PointPolicy currentPolicy() {
        return policyRepository.findCurrent()
                .orElseThrow(() -> new PointException("POLICY_NOT_FOUND", HttpStatus.INTERNAL_SERVER_ERROR,
                        "포인트 정책이 설정되지 않았습니다."));
    }
}
