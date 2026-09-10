package kr.co.freepoint.domain.use;

import jakarta.persistence.*;
import kr.co.freepoint.domain.vo.PointAmount;
import kr.co.freepoint.domain.vo.PointKey;

@Entity
@Table(name = "point_use_allocation")
public class PointUseAllocationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "use_id", nullable = false, updatable = false)
    private PointUse use;

    @Column(nullable = false, updatable = false, length = 36)
    private String earnPointKey;

    @Column(nullable = false, updatable = false)
    private long allocatedAmount;

    @Column(nullable = false)
    private long canceledAmount;

    @Column(nullable = false, updatable = false)
    private int seq;

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
