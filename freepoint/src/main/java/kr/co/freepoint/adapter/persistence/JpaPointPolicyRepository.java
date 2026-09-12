package kr.co.freepoint.adapter.persistence;

import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.policy.PointPolicyRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaPointPolicyRepository implements PointPolicyRepository {

    private final SpringDataPointPolicyRepository springDataRepository;

    JpaPointPolicyRepository(SpringDataPointPolicyRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public PointPolicy save(PointPolicy policy) {
        return springDataRepository.save(policy);
    }

    @Override
    public Optional<PointPolicy> findCurrent() {
        return springDataRepository.findAll().stream().findFirst();
    }
}
