package kr.co.freepoint.domain.policy;

import java.util.Optional;

public interface PointPolicyRepository {
    PointPolicy save(PointPolicy policy);
    Optional<PointPolicy> findCurrent();
}
