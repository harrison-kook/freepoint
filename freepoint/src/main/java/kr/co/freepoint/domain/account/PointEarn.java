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
    private Long id; // 적립 내역 ID (PK)

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String pointKey; // 적립 건을 외부에 식별시키는 고유 키(UUID)

    @Column(nullable = false, updatable = false)
    private long amount; // 최초 적립 금액

    @Column(nullable = false)
    private long remainingAmount; // 사용/취소 후 남은 잔여 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EarnType earnType; // 적립 유형(수동 관리자 적립, 만료 복원 등)

    @Column(nullable = false, updatable = false)
    private Instant earnedAt; // 적립 발생 시각

    @Column(nullable = false, updatable = false)
    private Instant expiresAt; // 적립분 만료 시각

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EarnStatus status; // 적립 상태(사용가능/소진/취소)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private PointAccount account; // 이 적립이 속한 포인트 계좌 (FK)

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
