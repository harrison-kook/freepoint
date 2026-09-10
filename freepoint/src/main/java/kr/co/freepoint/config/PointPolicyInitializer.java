package kr.co.freepoint.config;

import kr.co.freepoint.domain.policy.PointPolicy;
import kr.co.freepoint.domain.policy.PointPolicyRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PointPolicyInitializer implements ApplicationRunner {

    private final PointPolicyRepository policyRepository;

    public PointPolicyInitializer(PointPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (policyRepository.findCurrent().isEmpty()) {
            policyRepository.save(new PointPolicy(100_000, 1_000_000, 1, 1825, 365));
        }
    }
}
