# 무료 포인트 시스템 (API)

적립/적립취소/사용/사용취소를 지원하는 무료 포인트 시스템 과제 구현입니다. 

## 기술 스택

- Java 21, Spring Boot 3.5.16
- Spring Data JPA + H2 (인메모리)
- Gradle (Groovy DSL)
- JUnit 5, AssertJ, Mockito, Spring MockMvc

## 빌드 및 실행

```bash
# 테스트 전체 실행
./gradlew test

# 빌드 (테스트 포함)
./gradlew build

# 애플리케이션 실행 (기본 포트 8080)
./gradlew bootRun
```

앱이 기동되면 기본 포인트 정책(1회 최대 적립 100,000P, 최대 보유한도 1,000,000P, 만료일 1일~1825일/기본 365일)이 자동으로 시딩되어 별도 설정 없이 바로 API를 호출할 수 있습니다(`PointPolicyInitializer`).

간단한 동작 확인:

```bash
curl -X POST http://localhost:8080/api/points/earn \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-1","amount":1000,"earnType":"NORMAL"}'

curl http://localhost:8080/api/points/accounts/user-1/balance
```

H2 콘솔은 `http://localhost:8080/h2-console` 에서 확인할 수 있습니다.
- JDBC URL: jdbc:h2:mem:freepoint
- User Name: SA
- Password: 빈칸                                                                                                                                                                 

## API 목록

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/points/earn` | 적립 (`userId`, `amount`, `earnType`, `expireDays`(선택)) |
| POST | `/api/points/earns/{pointKey}/cancel` | 적립취소 (미사용 적립만 가능) |
| GET | `/api/points/earns/{pointKey}` | 적립 상세 조회 (사용처 추적 포함) |
| POST | `/api/points/use` | 사용 (`userId`, `orderNo`, `amount`) |
| POST | `/api/points/uses/{pointKey}/cancel` | 사용취소 (`amount`, 전체/부분) |
| GET | `/api/points/accounts/{userId}/balance` | 계정 총 잔액 조회 |
| GET | `/api/admin/point-policy` | 정책 조회 |
| PUT | `/api/admin/point-policy` | 정책 변경 (하드코딩 없이 한도/만료일 제어) |

에러 응답은 공통 형식(`{"errorCode": "...", "message": "..."}`)으로 내려갑니다(`GlobalExceptionHandler`).

## 설계 개요

- **개발 방식**: TDD + DDD방식. 구현 전에 테스트 시나리오/케이스를 먼저 작성했고, 7단계로 나누어 단계별로 진행했습니다.
- **애그리게잇**: `PointAccount`(계정 + 내부의 적립 lot인 `PointEarn`)와 `PointUse`(사용 + 배분 라인 `PointUseAllocationLine`)를 별도 애그리게잇으로 분리했습니다. 사용취소처럼 두 애그리게잇을 모두 건드리는 유스케이스는 애플리케이션 서비스(`CancelPointUseService`)가 한 트랜잭션에서 조율합니다.
- **핵심 도메인 로직**: 적립 lot의 소진 우선순위(관리자 지급 우선 → 만료 임박 순), 소진했던 순서 그대로 복원하는 부분 취소, 만료된 적립을 취소 복원할 때 신규 적립(`RESTORED_EXPIRED`)으로 처리하는 로직이 `PointAccount`/`PointEarn`/`PointUse` 안에 캡슐화되어 있습니다.
- **동시성**: 적립·사용·적립취소·사용취소 등 계정을 변경하는 모든 경로가 계정 조회 시 비관적 락(`@Lock(PESSIMISTIC_WRITE)`)을 사용합니다. 사용↔적립취소가 같은 적립건을 동시에 건드리는 경쟁 상태를 실제로 재현(둘 다 성공해 데이터가 깨지는 것을 확인)한 뒤 락으로 고쳤습니다.


## ERD

렌더링 도구(graphviz/mermaid-cli 등)가 설치되어 있지 않아 실제 JPA 엔티티를 기준으로 SVG를 직접 작성했습니다.

![ERD](src/main/resources/erd.svg)

원본 파일: [`src/main/resources/erd.svg`](src/main/resources/erd.svg)

`point_use_allocation.earn_point_key → point_earn.point_key`는 DB FK가 아니라 값 매칭입니다. `PointAccount`와 `PointUse`를 별도 애그리게잇으로 설계했기 때문에 일부러 FK로 묶지 않았습니다.

## AWS 아키텍처 (예시, 옵션)

실제 배포 환경을 가정했을 때의 구성 예시입니다. Route 53 → ALB → ECS Fargate(Spring Boot) → RDS(Multi-AZ) 형태이며, 과제 실행 환경의 H2는 운영 환경에서 RDS 등으로 교체가 필요합니다.

![AWS Architecture](src/main/resources/aws-architecture.svg)

원본 파일: [`src/main/resources/aws-architecture.svg`](src/main/resources/aws-architecture.svg)

## 설계 트레이드오프 및 가정

명세에 명시되지 않아 임의로 정한 부분입니다.

1. **만료 후 재적립(`RESTORED_EXPIRED`)의 새 만료일**: 명세에 언급이 없어 취소 시점 기준으로 정책의 기본 만료일(`defaultExpireDays`)을 새로 적용한다고 가정했습니다.
2. **최대 보유한도 초과 시 동작**: 초과분만 잘라 적립하는 대신, 적립 자체를 거부(400)합니다.
3. **사용 우선순위 동률 처리**: 관리자 지급 내에서도 여러 건이면 만료일이 짧은 순, 만료일까지 같으면 적립된 순(FIFO)으로 처리합니다.
4. **인증/인가**: 관리자 API(`/api/admin/**`)와 일반 API를 URL로만 구분했고, 별도 인증은 과제 범위상 구현하지 않았습니다. 실제 서비스라면 Spring Security 등으로 관리자 인증이 필요합니다.
5. **JPA 매핑**: 완전한 헥사고날 분리(도메인 모델과 JPA 엔티티를 별도 클래스로) 대신, 도메인 애그리게잇 자체를 JPA `@Entity`로 선언하는 실용적 접근을 택했습니다. 도메인 로직은 애그리게잇의 메서드 안에만 두고, 서비스/컨트롤러에서 필드를 직접 조작하지 않는 것을 원칙으로 했습니다.

## 알려진 한계

- 관리자 API에 인증이 없습니다 (위 가정 4 참고).
- 페이지네이션이 필요한 목록 조회 API(예: 계정별 적립 내역 전체 목록)는 구현하지 않았습니다. 현재는 특정 pointKey 단건 조회(`GET /api/points/earns/{pointKey}`)와 잔액 조회만 제공합니다.


## TDD 적용 순서

1. **도메인 계층 단위테스트** 먼저 작성 (S1~S29 중 계정/적립/사용취소 도메인 규칙) → `PointAccount`, `PointUse` 등 애그리게잇의 동작을 리치 도메인 모델로 구현
2. **애플리케이션 서비스 테스트** (유스케이스 단위, 리포지토리는 실제 구현 또는 인메모리 fake 사용)
3. **API/통합테스트** — S30(예시 시나리오 재현)을 최종 인수 테스트로 작성, 실제 H2 DB + 전체 스프링 컨텍스트로 검증

> - **계층**: `Domain`(애그리게잇 단위테스트) / `App`(애플리케이션 서비스, repository는 fake/in-memory) / `API`(SpringBootTest + H2, 실제 엔드포인트)
> - 정책 기본값(테스트 기준값): 1회 최대 적립 100,000P, 최대 보유한도 1,000,000P, 만료일 1일~1825일(5년) 미만, 기본 365일

```
Test
kr.co.freepoint
 ├─ application
 │   ├─ CancelEarnServiceTest   
 │   ├─ EarnPointServiceTest            
 │   ├─ FakePointAccountRepository      
 │   └─ FakePointPolicyRepository 
 ├─ domain    
 │   ├─ account
 │   │    ├─ PointEarnTest
 │   │    └─ PointEranCancelTest
 │   └─ use
 │        ├─ PointUseCancelTest
 │        └─ PointUseTest
 ├─ scenario
 │   ├─ ConcurrencyApiTest
 │   ├─ QueryApiTest
 │   └─ ScenarioTest
 └─ testsupport          
     ├─ MutableClock
     └─ PointPolicyFixtures
```     

## 1. 적립

| TC | 시나리오 | 계층 | Given | When | Then |
|---|---|---|---|---|---|
| TC-EARN-001 | 정상적으로 금액/만료일을 지정해 적립한다 | Domain | 계정 잔액 0 | amount=1000, expireDays=30 로 적립 | pointKey 발급, remainingAmount=1000, expiresAt=적립일+30일, 계정 총 잔액 1000 |
| TC-EARN-002 | 만료일을 지정하지 않으면 기본 365일이 적용된다 | Domain | 계정 잔액 0 | amount=1000, expireDays 미지정 | expiresAt = 적립일 + 정책.defaultExpireDays(365) |
| TC-EARN-003 | 적립 금액이 1P 미만(0 또는 음수)이면 실패한다 | Domain | - | amount=0 으로 적립 시도 | `InvalidPointAmountException` (또는 동등 예외) 발생, 적립 미생성 |
| TC-EARN-004 | 적립 금액이 1P 미만(0 또는 음수)이면 실패한다 | Domain | - | amount=-100 으로 적립 시도 | 예외 발생 |
| TC-EARN-005 | 적립 금액이 정책의 1회 최대 적립한도를 초과하면 실패한다 | Domain | 정책.maxEarnAmount=100000 | amount=100001 로 적립 시도 | 예외 발생 |
| TC-EARN-006 | 적립 금액이 정책의 1회 최대 적립한도와 정확히 같으면 성공한다 (경계값) | Domain | 정책.maxEarnAmount=100000 | amount=100000 으로 적립 | 성공 (경계값) |
| TC-EARN-007 | 적립 후 총 보유액이 정책의 최대 보유한도를 초과하면 실패한다 | Domain | 계정 현재 잔액 999,000, 정책.maxBalance=1,000,000 | amount=2000 적립 시도 (합계 1,001,000) | 예외 발생, 적립 미생성 |
| TC-EARN-008 | 적립 후 총 보유액이 정책의 최대 보유한도와 정확히 같으면 성공한다 (경계값) | Domain | 계정 현재 잔액 999,000, 정책.maxBalance=1,000,000 | amount=1000 적립 (합계 정확히 1,000,000) | 성공 (경계값) |
| TC-EARN-009 | 만료일수가 1일 미만이면 실패, 정확히 1일이면 성공한다 | Domain | - | expireDays=0 으로 적립 시도 | 예외 발생 |
| TC-EARN-010 | 만료일수가 1일 미만이면 실패, 정확히 1일이면 성공한다 | Domain | - | expireDays=1 로 적립 | 성공 |
| TC-EARN-011 | 만료일수가 5년 이상이면 실패, 5년 미만 최댓값이면 성공한다 | Domain | - | expireDays=1825(5년) 로 적립 시도 | 예외 발생 (5년 미만이어야 함) |
| TC-EARN-012 | 만료일수가 5년 이상이면 실패, 5년 미만 최댓값이면 성공한다 | Domain | - | expireDays=1824 로 적립 | 성공 |
| TC-EARN-013 | 관리자 수기 지급 적립은 일반 적립과 구분되는 타입으로 저장/조회된다 | Domain | - | 관리자 수기 지급으로 amount=1000 적립 | earnType=MANUAL_ADMIN 으로 저장됨, 일반 적립과 조회 시 구분됨 |
| TC-EARN-014 | 정책(1회한도/보유한도/만료일범위)을 변경하면 이후 적립 요청에 즉시 반영된다 (하드코딩 금지 검증) | App | 정책.maxEarnAmount=100000 | 정책을 maxEarnAmount=50000 으로 변경 후 amount=60000 적립 시도 | 예외 발생 (변경된 정책이 즉시 반영됨) |

## 2. 적립취소

| TC | 시나리오 | 계층 | Given | When | Then |
|---|---|---|---|---|---|
| TC-EARNCANCEL-001 | 전혀 사용되지 않은 적립은 전액 취소할 수 있다 | Domain | pointKey=A 로 1000원 적립, 미사용 | A 적립취소 | A.remainingAmount=0, A.status=CANCELED, 계정 잔액 -1000 |
| TC-EARNCANCEL-002 | 일부라도 사용된 적립은 취소할 수 없다 | Domain | A(1000원 적립) 중 300원 사용됨 (remaining=700) | A 적립취소 시도 | 예외 발생, A 상태 변화 없음 |
| TC-EARNCANCEL-003 | 이미 취소된 적립을 다시 취소하면 실패한다 | Domain | A 적립 후 이미 취소됨 | A 재취소 시도 | 예외 발생 |
| TC-EARNCANCEL-004 | 존재하지 않는 pointKey로 취소를 시도하면 실패한다 | App | - | 존재하지 않는 pointKey로 취소 API 호출 | 404 또는 NotFound 예외 |

## 3. 사용

| TC | 시나리오 | 계층 | Given | When | Then |
|---|---|---|---|---|---|
| TC-USE-001 | 주문번호와 함께 사용하면 사용 이력에 주문번호가 기록된다 | Domain/App | 계정 잔액 1000 | orderNo="A1234", amount=500 사용 | PointUse 생성, orderNo="A1234" 기록, pointKey 발급 |
| TC-USE-002 | 보유 잔액보다 큰 금액을 사용하려 하면 실패한다 | Domain | 계정 잔액 500 | amount=600 사용 시도 | 예외 발생(잔액부족), 상태 변화 없음 |
| TC-USE-003 | 여러 적립건에 걸쳐 소진될 경우, 적립건별 소진 금액이 1원 단위로 정확히 기록된다 | Domain | A(1000, 만료 늦음), B(500, 만료 늦음) 순서로 적립 | orderNo="A1234", amount=1200 사용 | allocation: A 1000 전액 + B 200, A.remaining=0, B.remaining=300 |
| TC-USE-004 | 관리자 수기지급 적립이 있으면 일반 적립보다 먼저 소진된다 | Domain | 일반적립 A(1000, 만료 짧음), 관리자지급 M(500, 만료 김) | amount=300 사용 | M에서 우선 소진 (M.remaining=200, A.remaining=1000 그대로) |
| TC-USE-005 | 동일 우선순위 그룹 내에서는 만료일이 짧게 남은 적립부터 소진된다 | Domain | 일반적립 A(만료 10일 뒤), B(만료 3일 뒤), 둘 다 500원 | amount=500 사용 | B가 먼저 전액 소진, A는 그대로 |
| TC-USE-006 | 이미 만료된 적립은 사용 대상에서 제외된다 | Domain | A(500, 이미 만료), B(500, 미만료) | amount=300 사용 | B에서만 소진, A는 소진 대상에서 제외 |
| TC-USE-007 | 소진되어 잔액이 0이 된 적립은 이후 소진 대상에서 제외된다 | Domain | A(500) | amount=500 사용 (전액 소진) | A.remainingAmount=0, A.status=EXHAUSTED, 이후 사용 요청 시 A 후보에서 제외 |

## 4. 사용취소

| TC | 시나리오 | 계층 | Given | When | Then |
|---|---|---|---|---|---|
| TC-USECANCEL-001 | 사용금액 전체를 취소하면 관련된 모든 적립건에 복원된다 | Domain | 사용 C: A에서 1000, B에서 200 소진 (총 1200) | C 전액(1200) 사용취소 | A.remaining +1000, B.remaining +200, C.canceledAmount=1200, C.status=FULLY_CANCELED |
| TC-USECANCEL-002 | 사용금액 일부만 취소하면 소진했던 순서 그대로 앞에서부터 복원된다 | Domain | 사용 C: A에서 1000(seq1), B에서 200(seq2) 소진 | C를 1100원 부분취소 | seq1(A)부터 최대치 복원: A +1000, 남은 100원은 seq2(B)에서 복원: B +100 |
| TC-USECANCEL-003 | 취소 요청 금액이 (원 사용금액 - 기취소금액)을 초과하면 실패한다 | Domain | 사용 C=1200, 이미 300원 취소됨(canceledAmount=300) | 추가로 1000원 취소 시도 (누적 1300 > 1200) | 예외 발생 |
| TC-USECANCEL-004 | 복원 대상 적립건이 아직 만료되지 않았다면 해당 적립건의 잔액이 복원된다 | Domain | 사용 C가 B(미만료)에서 200 소진 | 200원 사용취소 | B.remainingAmount +200 (신규 적립 생성 없음) |
| TC-USECANCEL-005 | 복원 대상 적립건이 이미 만료되었다면, 복원 대신 동일 금액의 신규 적립(새 pointKey)이 생성된다 | Domain | 사용 C가 A(이미 만료됨)에서 1000 소진 | 1000원 사용취소 | A.remainingAmount은 변하지 않음(만료 상태 유지), 신규 PointEarn(E) 생성, E.amount=1000, E.remainingAmount=1000 |
| TC-USECANCEL-006 | 신규 적립으로 복원된 건은 원래 적립과 구분되는 별도 타입(RESTORED_EXPIRED)을 가진다 | Domain | TC-USECANCEL-005 상황 | 신규 적립 E 생성 후 | E.earnType=RESTORED_EXPIRED, E.pointKey는 A와 다른 새 값, E는 사용취소 이벤트를 참조(추적 가능) |
| TC-USECANCEL-007 | 부분취소 후 남은 사용금액에 대해 추가로 부분취소할 수 있다 (누적 취소 검증) | Domain | 사용 C=1200원, 1차 취소 500원 완료(canceledAmount=500) | 2차로 700원 취소 요청(누적 정확히 1200) | 성공, C.status=FULLY_CANCELED |

## 5. 통합 시나리오 (명세 예시 재현)

| TC | 시나리오 | 계층 | 단계 | 검증 |
|---|---|---|---|---|
| TC-SCENARIO-001 | 1000원 적립(A) → 500원 적립(B) → 주문 A1234에서 1200원 사용(C: A전액+B일부 소진) → A 만료 → C의 1200원 중 1100원 부분취소(D: A분 1000원은 신규적립 E, B분 100원은 B에 복원) 전체 플로우를 순서대로 수행하며 매 단계 잔액/상태를 검증한다 | API (SpringBootTest) | 1) 1000원 적립 → pointKey A | 계정 잔액 0→1000 |
| | | | 2) 500원 적립 → pointKey B | 계정 잔액 1000→1500 |
| | | | 3) 주문 A1234, 1200원 사용 → pointKey C | 계정 잔액 1500→300, A소진 1000(잔액0), B소진 200(잔액300) |
| | | | 4) A 만료 처리(테스트에서 시간 조작 또는 만료일 도달 시뮬레이션) | A.expiresAt이 현재보다 과거가 됨 |
| | | | 5) C의 1200원 중 1100원 부분 사용취소 → pointKey D | 계정 잔액 300→1400 |
| | | | | A는 만료 상태이므로 복원 대신 신규적립 E 생성(1000원) |
| | | | | B는 미만료이므로 잔액 300→400원으로 복원 |
| | | | | C는 남은 취소가능금액 100원 (1200-1100) |
| | | | 최종 검증 | 계정 총 잔액=1400, 적립 내역: A(만료,잔액0), B(잔액400), E(잔액1000, RESTORED_EXPIRED) |

> 시간 조작을 위해 `Clock`을 DI로 분리하고, 테스트에서 `Clock.fixed(...)` 또는 조작 가능한 테스트용 Clock 구현체를 주입합니다.

## 6. 조회

| TC | 시나리오 | 계층 | Given | When | Then |
|---|---|---|---|---|---|
| TC-QUERY-001 | 특정 적립건(pointKey) 조회 시 해당 적립이 사용된 주문/금액 내역이 함께 조회된다 (추적성) | API | A 적립에서 1000원이 사용됨 | GET /api/points/earns/{A의 pointKey} | 응답에 사용 내역(주문번호, 소진금액) 포함 |
| TC-QUERY-002 | 계정의 현재 총 잔액을 조회한다 | API | 계정에 적립 A(1000), B(500 중 200 사용) 존재 | GET /api/points/accounts/{userId}/balance | 총 잔액 = 1000 + 300 = 1300 |

## 7. 동시성 (선택)

| TC | 시나리오 | 계층 | Given | When | Then |
|---|---|---|---|---|---|
| TC-CONCURRENCY-001 | 동일 계정에 대해 동시에 두 건의 사용 요청이 들어와 합계가 잔액을 초과하는 경우, 하나만 성공하고 다른 하나는 실패한다 | API | 계정 잔액 1000 | 800원 사용 요청 2건을 동시에 전송 | 하나만 성공(200), 다른 하나는 잔액부족으로 실패(4xx), 최종 잔액=200 |

