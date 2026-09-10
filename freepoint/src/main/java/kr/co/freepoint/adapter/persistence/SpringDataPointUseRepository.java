package kr.co.freepoint.adapter.persistence;

import kr.co.freepoint.domain.use.PointUse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataPointUseRepository extends JpaRepository<PointUse, Long> {

    Optional<PointUse> findByPointKey(String pointKey);

    List<PointUse> findAllByAllocations_EarnPointKey(String earnPointKey);
}
