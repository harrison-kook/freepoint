package kr.co.freepoint.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.freepoint.adapter.web.EarnRequest;
import kr.co.freepoint.adapter.web.UseRequest;
import kr.co.freepoint.domain.account.EarnType;
import kr.co.freepoint.domain.policy.PointPolicyRepository;
import kr.co.freepoint.testsupport.PointPolicyFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PointPolicyRepository policyRepository;

    @BeforeEach
    void setUp() {
        PointPolicyFixtures.resetToDefault(policyRepository);
    }

    /**
     * TC-QUERY-001
     * 특정 적립건(pointKey) 조회 시 해당 적립이 사용된 주문/금액 내역이 함께 조회된다 (추적성)
     * Given : A 적립에서 1000원이 사용됨
     * When : GET /api/points/earns/{A의 pointKey}
     * Then : 응답에 사용 내역(주문번호, 소진금액) 포함
     */
    @Test
    void TcQuery001() throws Exception {
        String userId = "user-query-1";
        String pointKeyA = earn(userId, 1000, EarnType.NORMAL, 100);
        use(userId, "A1234", 1000);

        mockMvc.perform(get("/api/points/earns/" + pointKeyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointKey").value(pointKeyA))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.remainingAmount").value(0))
                .andExpect(jsonPath("$.usages[0].orderNo").value("A1234"))
                .andExpect(jsonPath("$.usages[0].amount").value(1000));
    }

    /**
     * TC-QUERY-002
     * 계정의 현재 총 잔액을 조회한다
     * Given : 계정에 적립 A(1000), B(500 중 200 사용) 존재
     * When : GET /api/points/accounts/{userId}/balance
     * Then : 총 잔액 = 1000 + 300 = 1300
     */
    @Test
    void TcQuery002() throws Exception {
        String userId = "user-query-2";
        earn(userId, 1000, EarnType.NORMAL, 100);
        earn(userId, 500, EarnType.NORMAL, 100);
        use(userId, "A5678", 200);

        mockMvc.perform(get("/api/points/accounts/" + userId + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1300));
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

    private void use(String userId, String orderNo, long amount) throws Exception {
        String body = objectMapper.writeValueAsString(new UseRequest(userId, orderNo, amount));
        mockMvc.perform(post("/api/points/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
