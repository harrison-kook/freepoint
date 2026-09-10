package kr.co.freepoint.domain.use;

import jakarta.persistence.*;
import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.vo.OrderNo;
import kr.co.freepoint.domain.vo.PointAmount;
import kr.co.freepoint.domain.vo.PointKey;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "point_use")
public class PointUse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String pointKey;

    @Column(nullable = false, updatable = false)
    private String orderNo;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private Instant usedAt;

    @Column(nullable = false)
    private long canceledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointUseStatus status;

    @OneToMany(mappedBy = "use", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<PointUseAllocationLine> allocations = new ArrayList<>();

    protected PointUse() {
    }

    private PointUse(OrderNo orderNo, PointAmount amount, Instant usedAt) {
        this.pointKey = PointKey.newKey().value();
        this.orderNo = orderNo.value();
        this.amount = amount.value();
        this.usedAt = usedAt;
        this.canceledAmount = 0L;
        this.status = PointUseStatus.USED;
    }

    public static PointUse create(OrderNo orderNo, PointAmount amount, List<Allocation> allocations, Instant usedAt) {
        PointUse use = new PointUse(orderNo, amount, usedAt);
        int seq = 0;
        for (Allocation allocation : allocations) {
            use.allocations.add(PointUseAllocationLine.create(use, allocation.earnPointKey(), allocation.amount(), seq++));
        }
        return use;
    }

    public List<Allocation> planCancel(PointAmount requestedAmount) {
        PointAmount newCanceledTotal = PointAmount.of(canceledAmount).add(requestedAmount);
        if (newCanceledTotal.isGreaterThan(amount())) {
            throw new PointException("CANCEL_AMOUNT_EXCEEDS_USED", HttpStatus.BAD_REQUEST,
                    "취소 요청 금액이 취소 가능한 한도를 초과합니다.");
        }

        List<Allocation> plan = new ArrayList<>();
        PointAmount remaining = requestedAmount;
        for (PointUseAllocationLine line : allocations) {
            if (remaining.value() == 0) {
                break;
            }
            PointAmount cancelable = line.cancelableAmount();
            if (cancelable.value() == 0) {
                continue;
            }
            PointAmount cancelAmount = cancelable.isLessThan(remaining) ? cancelable : remaining;
            line.markCanceled(cancelAmount);
            plan.add(new Allocation(line.earnPointKey(), cancelAmount));
            remaining = remaining.subtract(cancelAmount);
        }

        this.canceledAmount = newCanceledTotal.value();
        this.status = this.canceledAmount == amount ? PointUseStatus.FULLY_CANCELED : PointUseStatus.PARTIALLY_CANCELED;

        return plan;
    }

    public PointKey pointKey() {
        return new PointKey(pointKey);
    }

    public OrderNo orderNo() {
        return new OrderNo(orderNo);
    }

    public PointAmount amount() {
        return PointAmount.of(amount);
    }

    public Instant usedAt() {
        return usedAt;
    }

    public PointAmount canceledAmount() {
        return PointAmount.of(canceledAmount);
    }

    public PointUseStatus status() {
        return status;
    }

    public List<PointUseAllocationLine> allocations() {
        return List.copyOf(allocations);
    }
}
