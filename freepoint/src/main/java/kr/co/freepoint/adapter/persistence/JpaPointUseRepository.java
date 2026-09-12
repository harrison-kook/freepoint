package kr.co.freepoint.adapter.persistence;

import kr.co.freepoint.domain.use.PointUse;
import kr.co.freepoint.domain.use.PointUseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JpaPointUseRepository implements PointUseRepository {

    private final SpringDataPointUseRepository springDataRepository;

    JpaPointUseRepository(SpringDataPointUseRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public PointUse save(PointUse use) {
        return springDataRepository.save(use);
    }

    @Override
    public Optional<PointUse> findByPointKey(String pointKey) {
        return springDataRepository.findByPointKey(pointKey);
    }

    @Override
    public List<PointUse> findAllByAllocationEarnPointKey(String earnPointKey) {
        return springDataRepository.findAllByAllocations_EarnPointKey(earnPointKey);
    }
}
