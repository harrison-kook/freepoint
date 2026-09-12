package kr.co.freepoint.application;

import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class FakePointAccountRepository implements PointAccountRepository {

    private final Map<String, PointAccount> store = new HashMap<>();

    @Override
    public PointAccount save(PointAccount account) {
        store.put(account.userId(), account);
        return account;
    }

    @Override
    public Optional<PointAccount> findByUserId(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public Optional<PointAccount> findByEarnPointKey(String pointKey) {
        return store.values().stream()
                .filter(account -> account.earns().stream()
                        .anyMatch(earn -> earn.pointKey().value().equals(pointKey)))
                .findFirst();
    }

    @Override
    public Optional<PointAccount> findByUserIdForUpdate(String userId) {
        return findByUserId(userId);
    }

    @Override
    public Optional<PointAccount> findByEarnPointKeyForUpdate(String pointKey) {
        return findByEarnPointKey(pointKey);
    }
}
