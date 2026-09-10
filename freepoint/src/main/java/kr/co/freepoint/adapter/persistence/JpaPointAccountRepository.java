package kr.co.freepoint.adapter.persistence;

import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaPointAccountRepository implements PointAccountRepository {

    private final SpringDataPointAccountRepository springDataRepository;

    JpaPointAccountRepository(SpringDataPointAccountRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public PointAccount save(PointAccount account) {
        return springDataRepository.save(account);
    }

    @Override
    public Optional<PointAccount> findByUserId(String userId) {
        return springDataRepository.findByUserId(userId);
    }

    @Override
    public Optional<PointAccount> findByEarnPointKey(String pointKey) {
        return springDataRepository.findByEarns_PointKey(pointKey);
    }

    @Override
    public Optional<PointAccount> findByUserIdForUpdate(String userId) {
        return springDataRepository.findByUserIdForUpdate(userId);
    }

    @Override
    public Optional<PointAccount> findByEarnPointKeyForUpdate(String pointKey) {
        return springDataRepository.findByEarnPointKeyForUpdate(pointKey);
    }
}
