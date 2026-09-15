package kr.co.freepoint.domain.use;

import java.util.List;
import java.util.Optional;

public interface PointUseRepository {

    PointUse save(PointUse use);

    Optional<PointUse> findByPointKey(String pointKey);

    Optional<PointUse> findByPointKeyForUpdate(String pointKey);

    List<PointUse> findAllByAllocationEarnPointKey(String earnPointKey);
}
