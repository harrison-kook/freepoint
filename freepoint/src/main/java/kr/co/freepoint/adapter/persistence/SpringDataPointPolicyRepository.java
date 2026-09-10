package kr.co.freepoint.adapter.persistence;

import kr.co.freepoint.domain.policy.PointPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPointPolicyRepository extends JpaRepository<PointPolicy, Long> {
}
