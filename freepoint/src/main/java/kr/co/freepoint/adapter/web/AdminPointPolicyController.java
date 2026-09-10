package kr.co.freepoint.adapter.web;

import kr.co.freepoint.application.AdminPointPolicyService;
import kr.co.freepoint.application.PointPolicyResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/point-policy")
public class AdminPointPolicyController {

    private final AdminPointPolicyService adminPointPolicyService;

    public AdminPointPolicyController(AdminPointPolicyService adminPointPolicyService) {
        this.adminPointPolicyService = adminPointPolicyService;
    }

    @GetMapping
    public PointPolicyResult get() {
        return adminPointPolicyService.get();
    }

    @PutMapping
    public PointPolicyResult update(@RequestBody PointPolicyRequest request) {
        return adminPointPolicyService.update(
                request.maxEarnAmount(),
                request.maxBalanceAmount(),
                request.minExpireDays(),
                request.maxExpireDays(),
                request.defaultExpireDays());
    }
}
