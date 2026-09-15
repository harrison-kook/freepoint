package kr.co.freepoint.application;

import kr.co.freepoint.domain.use.PointUse;
import kr.co.freepoint.domain.use.PointUseRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class FakePointUseRepository implements PointUseRepository {

    private final Map<String, PointUse> store = new HashMap<>();

    @Override
    public PointUse save(PointUse use) {
        store.put(use.pointKey().value(), use);
        return use;
    }

    @Override
    public Optional<PointUse> findByPointKey(String pointKey) {
        return Optional.ofNullable(store.get(pointKey));
    }

    @Override
    public Optional<PointUse> findByPointKeyForUpdate(String pointKey) {
        return findByPointKey(pointKey);
    }

    @Override
    public List<PointUse> findAllByAllocationEarnPointKey(String earnPointKey) {
        return store.values().stream()
                .filter(use -> use.allocations().stream()
                        .anyMatch(line -> line.earnPointKey().value().equals(earnPointKey)))
                .toList();
    }
}
