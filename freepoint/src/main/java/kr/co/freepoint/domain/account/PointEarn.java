package kr.co.freepoint.domain.account;

import jakarta.persistence.*;
import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.vo.ExpiryPeriod;
import kr.co.freepoint.domain.vo.PointAmount;
import kr.co.freepoint.domain.vo.PointKey;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Entity
@Table(name = "point_earn")
public class PointEarn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String pointKey;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false)
    private long remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EarnType earnType;

    @Column(nullable = false, updatable = false)
    private Instant earnedAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EarnStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private PointAccount account;

    protected PointEarn() {
    }

    private PointEarn(PointAccount account, PointAmount amount, EarnType earnType, Instant earnedAt, Instant expiresAt) {
        this.account = account;
        this.pointKey = PointKey.newKey().value();
        this.amount = amount.value();
        this.remainingAmount = amount.value();
        this.earnType = earnType;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
        this.status = EarnStatus.ACTIVE;
    }

    static PointEarn create(PointAccount account, PointAmount amount, EarnType earnType, Instant earnedAt, ExpiryPeriod expiryPeriod) {
        return new PointEarn(account, amount, earnType, earnedAt, expiryPeriod.expiresAtFrom(earnedAt));
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsable(Instant now) {
        return status == EarnStatus.ACTIVE && !isExpired(now);
    }

    void consume(PointAmount amount) {
        this.remainingAmount = remainingAmount().subtract(amount).value();
        if (this.remainingAmount == 0) {
            this.status = EarnStatus.EXHAUSTED;
        }
    }

    void restore(PointAmount amount) {
        this.remainingAmount = remainingAmount().add(amount).value();
        if (this.status == EarnStatus.EXHAUSTED) {
            this.status = EarnStatus.ACTIVE;
        }
    }

    void cancel() {
        if (status == EarnStatus.CANCELED) {
            throw new PointException("EARN_ALREADY_CANCELED", HttpStatus.BAD_REQUEST,
                    "이미 취소된 적립입니다: " + pointKey);
        }
        if (remainingAmount != amount) {
            throw new PointException("EARN_ALREADY_USED", HttpStatus.BAD_REQUEST,
                    "일부라도 사용된 적립은 취소할 수 없습니다: " + pointKey);
        }
        this.remainingAmount = 0L;
        this.status = EarnStatus.CANCELED;
    }

    public EarnStatus status() {
        return status;
    }

    public PointKey pointKey() {
        return new PointKey(pointKey);
    }

    public PointAmount amount() {
        return PointAmount.of(amount);
    }

    public PointAmount remainingAmount() {
        return PointAmount.of(remainingAmount);
    }

    public EarnType earnType() {
        return earnType;
    }

    public Instant earnedAt() {
        return earnedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
