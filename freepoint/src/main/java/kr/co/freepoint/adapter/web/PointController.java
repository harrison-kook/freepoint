package kr.co.freepoint.adapter.web;

import jakarta.validation.Valid;
import kr.co.freepoint.application.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/points")
public class PointController {
    private final EarnPointService earnPointService;
    private final CancelEarnService cancelEarnService;
    private final UsePointService usePointService;
    private final CancelPointUseService cancelPointUseService;
    private final PointQueryService pointQueryService;

    public PointController(EarnPointService earnPointService, CancelEarnService cancelEarnService,
                           UsePointService usePointService, CancelPointUseService cancelPointUseService,
                           PointQueryService pointQueryService) {
        this.earnPointService = earnPointService;
        this.cancelEarnService = cancelEarnService;
        this.usePointService = usePointService;
        this.cancelPointUseService = cancelPointUseService;
        this.pointQueryService = pointQueryService;
    }

    @PostMapping("/earn")
    public PointEarnResult earn(@Valid @RequestBody EarnRequest request) {
        return earnPointService.earn(request.userId(), request.amount(), request.earnType(), request.expireDays());
    }

    @PostMapping("/earns/{pointKey}/cancel")
    public void cancelEarn(@PathVariable String pointKey) {
        cancelEarnService.cancel(pointKey);
    }

    @PostMapping("/use")
    public PointUseResult use(@Valid @RequestBody UseRequest request) {
        return usePointService.use(request.userId(), request.orderNo(), request.amount());
    }

    @PostMapping("/uses/{pointKey}/cancel")
    public PointUseCancelResult cancelUse(@PathVariable String pointKey, @RequestBody UseCancelRequest request) {
        return cancelPointUseService.cancel(pointKey, request.amount());
    }

    @GetMapping("/accounts/{userId}/balance")
    public BalanceResult balance(@PathVariable String userId) {
        return pointQueryService.balance(userId);
    }

    @GetMapping("/earns/{pointKey}")
    public PointEarnDetailResult earnDetail(@PathVariable String pointKey) {
        return pointQueryService.earnDetail(pointKey);
    }
}
