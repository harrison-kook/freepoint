package kr.co.freepoint.domain.account;

import jakarta.persistence.*;
import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.use.Allocation;
import kr.co.freepoint.domain.use.PointUse;
import kr.co.freepoint.domain.vo.ExpiryPeriod;
import kr.co.freepoint.domain.vo.OrderNo;
import kr.co.freepoint.domain.vo.PointAmount;
import kr.co.freepoint.domain.vo.PointKey;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "point_account")
public class PointAccount {

    private static final Comparator<PointEarn> USE_PRIORITY = Comparator
            .comparing((PointEarn earn) -> earn.earnType() == EarnType.MANUAL_ADMIN ? 0 : 1)
            .thenComparing(PointEarn::expiresAt)
            .thenComparing(PointEarn::earnedAt);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 포인트 계좌 ID (PK)

    @Column(nullable = false, unique = true, updatable = false)
    private String userId; // 계좌 소유자 사용자 ID

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PointEarn> earns = new ArrayList<>(); // 이 계좌에 속한 적립 내역 목록

    protected PointAccount() {
    }

    private PointAccount(String userId) {
        this.userId = userId;
    }

    public static PointAccount create(String userId) {
        return new PointAccount(userId);
    }

    public PointEarn earn(PointAmount amount, EarnType earnType, ExpiryPeriod requestedExpiry, PointPolicy policy, Instant now) {
        policy.validateEarnAmount(amount);
        ExpiryPeriod expiry = policy.resolveExpiryPeriod(requestedExpiry);
        PointAmount balanceAfterEarn = totalBalance(now).add(amount);
        policy.validateBalanceAfterEarn(balanceAfterEarn);

        PointEarn earn = PointEarn.create(this, amount, earnType, now, expiry);
        earns.add(earn);
        return earn;
    }

    public PointEarn cancelEarn(PointKey pointKey) {
        PointEarn earn = findEarn(pointKey);
        earn.cancel();
        return earn;
    }

    private PointEarn findEarn(PointKey pointKey) {
        return earns.stream()
                .filter(earn -> earn.pointKey().equals(pointKey))
                .findFirst()
                .orElseThrow(() -> new PointException("EARN_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "적립 내역을 찾을 수 없습니다: " + pointKey.value()));
    }

    public PointUse use(OrderNo orderNo, PointAmount amount, Instant now) {
        List<PointEarn> usable = earns.stream()
                .filter(earn -> earn.isUsable(now))
                .sorted(USE_PRIORITY)
                .toList();

        if (totalBalance(now).isLessThan(amount)) {
            throw new PointException("INSUFFICIENT_BALANCE", HttpStatus.BAD_REQUEST, "포인트 잔액이 부족합니다.");
        }

        List<Allocation> allocations = new ArrayList<>();
        PointAmount remaining = amount;
        for (PointEarn earn : usable) {
            if (remaining.value() == 0) {
                break;
            }
            PointAmount consumeAmount = earn.remainingAmount().isLessThan(remaining) ? earn.remainingAmount() : remaining;
            earn.consume(consumeAmount);
            allocations.add(new Allocation(earn.pointKey(), consumeAmount));
            remaining = remaining.subtract(consumeAmount);
        }

        return PointUse.create(orderNo, amount, allocations, now);
    }

    public void restore(List<Allocation> restorePlan, PointPolicy policy, Instant now) {
        for (Allocation allocation : restorePlan) {
            PointEarn earn = findEarn(allocation.earnPointKey());
            if (earn.isExpired(now)) {
                ExpiryPeriod expiry = policy.resolveExpiryPeriod(null);
                earns.add(PointEarn.create(this, allocation.amount(), EarnType.RESTORED_EXPIRED, now, expiry));
            } else {
                earn.restore(allocation.amount());
            }
        }
    }

    public PointAmount totalBalance(Instant now) {
        return earns.stream()
                .filter(earn -> earn.isUsable(now))
                .map(PointEarn::remainingAmount)
                .reduce(PointAmount.zero(), PointAmount::add);
    }

    public String userId() {
        return userId;
    }

    public List<PointEarn> earns() {
        return List.copyOf(earns);
    }
}
