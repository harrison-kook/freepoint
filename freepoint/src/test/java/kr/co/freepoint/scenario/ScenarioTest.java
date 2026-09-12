package kr.co.freepoint.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.freepoint.adapter.web.EarnRequest;
import kr.co.freepoint.adapter.web.UseCancelRequest;
import kr.co.freepoint.adapter.web.UseRequest;
import kr.co.freepoint.domain.account.EarnType;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;
import kr.co.freepoint.domain.account.PointEarn;
import kr.co.freepoint.domain.policy.PointPolicyRepository;
import kr.co.freepoint.domain.vo.PointAmount;
import kr.co.freepoint.testsupport.MutableClock;
import kr.co.freepoint.testsupport.PointPolicyFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ScenarioTest.ClockTestConfig.class)
@Transactional
class ScenarioTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClock clock;

    @Autowired
    private PointPolicyRepository policyRepository;

    @Autowired
    private PointAccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        clock.advanceTo(START);
        PointPolicyFixtures.resetToDefault(policyRepository);
    }

    /**
     * TC-SCENARIO-001
     * 1000원 적립(A) → 500원 적립(B) → 주문 A1234에서 1200원 사용(C: A전액+B일부 소진)
     * → A 만료
     * → C의 1200원 중 1100원 부분취소(D: A분 1000원은 신규적립 E, B분 100원은 B에 복원)
     * 전체 플로우를 순서대로 수행하며 매 단계 잔액/상태를 검증한다
     * 1) 1000원 적립 → pointKey A
     * 2) 500원 적립 → pointKey B
     * 3) 주문 A1234, 1200원 사용 → pointKey C
     * 4) A 만료 처리(테스트에서 시간 조작 또는 만료일 도달 시뮬레이션)
     * 5) C의 1200원 중 1100원 부분 사용취소 → pointKey D
     */
    @Test
    void TcScenario001() throws Exception {
        String userId = "user-1";

        // 1) 1000원 적립 -> A (30일 후 만료)
        String pointKeyA = earn(userId, 1000, EarnType.NORMAL, 30);
        assertBalance(userId, 1000);

        // 2) 500원 적립 -> B (만료 넉넉히)
        String pointKeyB = earn(userId, 500, EarnType.NORMAL, 400);
        assertBalance(userId, 1500);

        // 3) 주문 A1234에서 1200원 사용 -> C
        String pointKeyC = use(userId, "A1234", 1200);
        assertBalance(userId, 300);

        // 4) A의 적립이 만료되었다
        clock.advanceTo(START.plus(31, ChronoUnit.DAYS));

        // 5) C의 1200원 중 1100원 부분 사용취소 -> D
        JsonNode cancelResult = cancelUse(pointKeyC, 1100);
        assertThat(cancelResult.get("canceledAmount").asLong()).isEqualTo(1100);
        assertThat(cancelResult.get("status").asText()).isEqualTo("PARTIALLY_CANCELED");

        assertBalance(userId, 1400);

        PointAccount account = accountRepository.findByUserId(userId).orElseThrow();
        Map<String, PointEarn> earnsByKey = account.earns().stream()
                .collect(Collectors.toMap(earn -> earn.pointKey().value(), earn -> earn));

        PointEarn earnA = earnsByKey.get(pointKeyA);
        assertThat(earnA.remainingAmount()).isEqualTo(PointAmount.zero());
        assertThat(earnA.isExpired(clock.instant())).isTrue();

        PointEarn earnB = earnsByKey.get(pointKeyB);
        assertThat(earnB.remainingAmount()).isEqualTo(PointAmount.of(400));

        PointEarn earnE = account.earns().stream()
                .filter(earn -> earn.earnType() == EarnType.RESTORED_EXPIRED)
                .findFirst()
                .orElseThrow();
        assertThat(earnE.amount()).isEqualTo(PointAmount.of(1000));
        assertThat(earnE.remainingAmount()).isEqualTo(PointAmount.of(1000));
    }

    private String earn(String userId, long amount, EarnType earnType, Integer expireDays) throws Exception {
        String body = objectMapper.writeValueAsString(new EarnRequest(userId, amount, earnType, expireDays));
        String response = mockMvc.perform(post("/api/points/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("pointKey").asText();
    }

    private String use(String userId, String orderNo, long amount) throws Exception {
        String body = objectMapper.writeValueAsString(new UseRequest(userId, orderNo, amount));
        String response = mockMvc.perform(post("/api/points/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("pointKey").asText();
    }

    private JsonNode cancelUse(String pointKey, long amount) throws Exception {
        String body = objectMapper.writeValueAsString(new UseCancelRequest(amount));
        String response = mockMvc.perform(post("/api/points/uses/" + pointKey + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void assertBalance(String userId, long expected) throws Exception {
        String response = mockMvc.perform(get("/api/points/accounts/" + userId + "/balance"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(response).get("balance").asLong()).isEqualTo(expected);
    }
}
