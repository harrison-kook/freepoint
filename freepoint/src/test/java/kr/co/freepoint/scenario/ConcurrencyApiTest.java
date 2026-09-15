package kr.co.freepoint.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.freepoint.adapter.web.EarnRequest;
import kr.co.freepoint.adapter.web.UseCancelRequest;
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

    /**
     * TC-CONCURRENCY-002
     * 동일 사용 건(PointUse)에 대해 부분취소 요청 두 건이 동시에 들어와 합계가 취소 가능 금액(사용금액-기취소금액)을 초과하는 경우,
     * 하나만 성공하고 다른 하나는 실패하며 사용금액을 초과해서 복원되지 않는다
     * Given : 2000원 적립 후 1200원 사용(사용 건 C, 잔액 800)
     * When : C에 대해 700원 부분취소 요청 2건을 동시에 전송
     * Then : 하나만 성공(200), 다른 하나는 취소한도초과로 실패(4xx), 최종 잔액=1500(=2000-1200+700).
     *        두 건 모두 성공하면 잔액이 2200이 되어 원 적립액(2000)을 초과하므로 이를 방지
     */
    @Test
    void TcConcurrency002() throws Exception {
        String userId = "user-concurrency-2";
        earn(userId, 2000, EarnType.NORMAL, 100);
        String usePointKey = useAndGetPointKey(userId, "ORDER-CANCEL", 1200);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return cancelUse(usePointKey, 700);
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

        // earn 2000 - use 1200 + 정확히 한 건만 복원된 700 = 1500 (두 건 모두 복원되면 2200이 되어 원래 적립액을 초과한다)
        mockMvc.perform(get("/api/points/accounts/" + userId + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500));
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

    private String useAndGetPointKey(String userId, String orderNo, long amount) throws Exception {
        String body = objectMapper.writeValueAsString(new UseRequest(userId, orderNo, amount));
        String response = mockMvc.perform(post("/api/points/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("pointKey").asText();
    }

    private int cancelUse(String pointKey, long amount) throws Exception {
        String body = objectMapper.writeValueAsString(new UseCancelRequest(amount));
        return mockMvc.perform(post("/api/points/uses/" + pointKey + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }
}
