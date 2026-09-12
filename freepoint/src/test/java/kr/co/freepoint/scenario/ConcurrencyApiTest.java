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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyApiTest {

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
     * TC-CONCURRENCY-001
     * 동일 계정에 대해 동시에 두 건의 사용 요청이 들어와 합계가 잔액을 초과하는 경우, 하나만 성공하고 다른 하나는 실패한다
     * Given : 계정 잔액 1000
     * When : 800원 사용 요청 2건을 동시에 전송
     * Then : 하나만 성공(200), 다른 하나는 잔액부족으로 실패(4xx), 최종 잔액=200
     */
    @Test
    void TcConcurrency001() throws Exception {
        String userId = "user-concurrency-1";
        earn(userId, 1000, EarnType.NORMAL, 100);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            String orderNo = "ORDER-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return use(userId, orderNo, 800);
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        List<Integer> statusCodes = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statusCodes.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        long successCount = statusCodes.stream().filter(code -> code == 200).count();
        long failureCount = statusCodes.stream().filter(code -> code >= 400).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);

        mockMvc.perform(get("/api/points/accounts/" + userId + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200));
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

    private int use(String userId, String orderNo, long amount) throws Exception {
        String body = objectMapper.writeValueAsString(new UseRequest(userId, orderNo, amount));
        return mockMvc.perform(post("/api/points/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }
}
