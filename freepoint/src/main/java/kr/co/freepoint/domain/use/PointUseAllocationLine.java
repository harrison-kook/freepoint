package kr.co.freepoint.domain.use;

import jakarta.persistence.*;
import kr.co.freepoint.domain.vo.PointAmount;
import kr.co.freepoint.domain.vo.PointKey;

@Entity
@Table(name = "point_use_allocation")
public class PointUseAllocationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 배분 라인 ID (PK)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "use_id", nullable = false, updatable = false)
    private PointUse use; // 이 배분이 속한 사용 건 (FK)

    @Column(nullable = false, updatable = false, length = 36)
    private String earnPointKey; // 차감 대상이 된 적립 건의 키(PointEarn.pointKey)

    @Column(nullable = false, updatable = false)
    private long allocatedAmount; // 해당 적립분에서 차감(배분)된 금액

    @Column(nullable = false)
    private long canceledAmount; // 이 배분 라인에서 취소된 금액

    @Column(nullable = false, updatable = false)
    private int seq; // 같은 사용 건 내에서 소진 순서(우선순위)

    protected PointUseAllocationLine() {
    }

    private PointUseAllocationLine(PointUse use, PointKey earnPointKey, PointAmount allocatedAmount, int seq) {
        this.use = use;
        this.earnPointKey = earnPointKey.value();
        this.allocatedAmount = allocatedAmount.value();
        this.canceledAmount = 0L;
        this.seq = seq;
    }

    static PointUseAllocationLine create(PointUse use, PointKey earnPointKey, PointAmount allocatedAmount, int seq) {
        return new PointUseAllocationLine(use, earnPointKey, allocatedAmount, seq);
    }

    PointAmount cancelableAmount() {
        return allocatedAmount().subtract(PointAmount.of(canceledAmount));
    }

    void markCanceled(PointAmount amount) {
        this.canceledAmount = PointAmount.of(canceledAmount).add(amount).value();
    }

    public PointKey earnPointKey() {
        return new PointKey(earnPointKey);
    }

    public PointAmount allocatedAmount() {
        return PointAmount.of(allocatedAmount);
    }

    public int seq() {
        return seq;
    }
}
