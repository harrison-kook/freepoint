package kr.co.freepoint.domain.account;

import java.util.Optional;

public interface PointAccountRepository {

    PointAccount save(PointAccount account);

    Optional<PointAccount> findByUserId(String userId);

    Optional<PointAccount> findByEarnPointKey(String pointKey);

    Optional<PointAccount> findByUserIdForUpdate(String userId);

    Optional<PointAccount> findByEarnPointKeyForUpdate(String pointKey);
}
