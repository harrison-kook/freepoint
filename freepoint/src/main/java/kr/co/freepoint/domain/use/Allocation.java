package kr.co.freepoint.domain.use;

import kr.co.freepoint.domain.vo.PointAmount;
import kr.co.freepoint.domain.vo.PointKey;

public record Allocation(PointKey earnPointKey, PointAmount amount) {
}
