package kr.co.freepoint.adapter.persistence;

import jakarta.persistence.LockModeType;
import kr.co.freepoint.domain.use.PointUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataPointUseRepository extends JpaRepository<PointUse, Long> {

    Optional<PointUse> findByPointKey(String pointKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from PointUse u where u.pointKey = :pointKey")
    Optional<PointUse> findByPointKeyForUpdate(@Param("pointKey") String pointKey);

    List<PointUse> findAllByAllocations_EarnPointKey(String earnPointKey);
}
