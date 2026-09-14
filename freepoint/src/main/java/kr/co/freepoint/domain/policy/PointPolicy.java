package kr.co.freepoint.domain.policy;

import jakarta.persistence.*;
import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.vo.ExpiryPeriod;
import kr.co.freepoint.domain.vo.PointAmount;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "point_policy")
public class PointPolicy {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 정책 ID (PK)

    @Column(nullable = false)
    private long maxEarnAmount; // 1회 최대 적립 가능 금액

    @Column(nullable = false)
    private long maxBalanceAmount; // 계좌가 보유할 수 있는 최대 잔액 한도

    @Column(nullable = false)
    private int minExpireDays; // 허용되는 최소 만료일수

    @Column(nullable = false)
    private int maxExpireDays; // 허용되는 최대 만료일수

    @Column(nullable = false)
    private int defaultExpireDays; // 만료일수 미지정 시 적용되는 기본값

    protected PointPolicy() {
    }

    public PointPolicy(long maxEarnAmount, long maxBalanceAmount, int minExpireDays, int maxExpireDays, int defaultExpireDays) {
        validate(maxEarnAmount, maxBalanceAmount, minExpireDays, maxExpireDays, defaultExpireDays);
        this.maxEarnAmount = maxEarnAmount;
        this.maxBalanceAmount = maxBalanceAmount;
        this.minExpireDays = minExpireDays;
        this.maxExpireDays = maxExpireDays;
        this.defaultExpireDays = defaultExpireDays;
    }

    public void validateEarnAmount(PointAmount amount) {
        if (amount.isLessThan(PointAmount.of(1)) || amount.isGreaterThan(PointAmount.of(maxEarnAmount))) {
            throw new PointException("EARN_AMOUNT_OUT_OF_RANGE", HttpStatus.BAD_REQUEST,
                    "적립 금액은 1 이상 " + maxEarnAmount + " 이하이어야 합니다.");
        }
    }

    public ExpiryPeriod resolveExpiryPeriod(ExpiryPeriod requested) {
        ExpiryPeriod period = requested != null ? requested : ExpiryPeriod.ofDays(defaultExpireDays);
        if (period.days() < minExpireDays || period.days() >= maxExpireDays) {
            throw new PointException("EXPIRE_PERIOD_OUT_OF_RANGE", HttpStatus.BAD_REQUEST,
                    "만료일수는 " + minExpireDays + "일 이상 " + maxExpireDays + "일 미만이어야 합니다.");
        }
        return period;
    }

    public void validateBalanceAfterEarn(PointAmount balanceAfterEarn) {
        if (balanceAfterEarn.isGreaterThan(PointAmount.of(maxBalanceAmount))) {
            throw new PointException("BALANCE_LIMIT_EXCEEDED", HttpStatus.BAD_REQUEST,
                    "적립 후 보유 포인트는 최대 보유 한도(" + maxBalanceAmount + ")를 초과할 수 없습니다.");
        }
    }

    public void update(long maxEarnAmount, long maxBalanceAmount, int minExpireDays, int maxExpireDays, int defaultExpireDays) {
        validate(maxEarnAmount, maxBalanceAmount, minExpireDays, maxExpireDays, defaultExpireDays);
        this.maxEarnAmount = maxEarnAmount;
        this.maxBalanceAmount = maxBalanceAmount;
        this.minExpireDays = minExpireDays;
        this.maxExpireDays = maxExpireDays;
        this.defaultExpireDays = defaultExpireDays;
    }

    private static void validate(long maxEarnAmount, long maxBalanceAmount, int minExpireDays, int maxExpireDays, int defaultExpireDays) {
        if (maxEarnAmount < 1) {
            throw new PointException("INVALID_POLICY", HttpStatus.BAD_REQUEST, "1회 최대 적립한도는 1 이상이어야 합니다.");
        }
        if (maxBalanceAmount < 1) {
            throw new PointException("INVALID_POLICY", HttpStatus.BAD_REQUEST, "최대 보유한도는 1 이상이어야 합니다.");
        }
        if (minExpireDays < 1) {
            throw new PointException("INVALID_POLICY", HttpStatus.BAD_REQUEST, "최소 만료일수는 1 이상이어야 합니다.");
        }
        if (maxExpireDays <= minExpireDays) {
            throw new PointException("INVALID_POLICY", HttpStatus.BAD_REQUEST, "최대 만료일수는 최소 만료일수보다 커야 합니다.");
        }
        if (defaultExpireDays < minExpireDays || defaultExpireDays >= maxExpireDays) {
            throw new PointException("INVALID_POLICY", HttpStatus.BAD_REQUEST,
                    "기본 만료일수는 최소 만료일수 이상, 최대 만료일수 미만이어야 합니다.");
        }
    }

    public long maxEarnAmount() {
        return maxEarnAmount;
    }

    public long maxBalanceAmount() {
        return maxBalanceAmount;
    }

    public int minExpireDays() {
        return minExpireDays;
    }

    public int maxExpireDays() {
        return maxExpireDays;
    }

    public int defaultExpireDays() {
        return defaultExpireDays;
    }
}
