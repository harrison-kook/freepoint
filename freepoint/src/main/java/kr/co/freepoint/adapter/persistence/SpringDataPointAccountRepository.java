package kr.co.freepoint.adapter.persistence;

import jakarta.persistence.LockModeType;
import kr.co.freepoint.domain.account.PointAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataPointAccountRepository extends JpaRepository<PointAccount, Long> {

    Optional<PointAccount> findByUserId(String userId);

    Optional<PointAccount> findByEarns_PointKey(String pointKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PointAccount a where a.userId = :userId")
    Optional<PointAccount> findByUserIdForUpdate(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PointAccount a join a.earns e where e.pointKey = :pointKey")
    Optional<PointAccount> findByEarnPointKeyForUpdate(@Param("pointKey") String pointKey);
}
