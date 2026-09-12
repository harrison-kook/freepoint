package kr.co.freepoint.application;

import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.policy.PointPolicyRepository;

import java.util.Optional;

class FakePointPolicyRepository implements PointPolicyRepository {

    private PointPolicy policy;

    @Override
    public PointPolicy save(PointPolicy policy) {
        this.policy = policy;
        return policy;
    }

    @Override
    public Optional<PointPolicy> findCurrent() {
        return Optional.ofNullable(policy);
    }
}
