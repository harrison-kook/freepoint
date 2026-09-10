# 무료 포인트 시스템 (API)

적립/적립취소/사용/사용취소를 지원하는 무료 포인트 시스템 과제 구현입니다. 명세는 [`무료 포인트 시스템 (API).md`](<./무료 포인트 시스템 (API).md>)를 따르며, 설계·진행 과정은 [`docs/`](./docs) 폴더에 문서로 남겼습니다.

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

- **개발 방식**: TDD(Red-Green-Refactor) + DDD. 구현 전에 테스트 시나리오/케이스를 먼저 작성했고, 8단계로 나누어 단계별로 진행했습니다.
- **애그리게잇**: `PointAccount`(계정 + 내부의 적립 lot인 `PointEarn`)와 `PointUse`(사용 + 배분 라인 `PointUseAllocationLine`)를 별도 애그리게잇으로 분리했습니다. 사용취소처럼 두 애그리게잇을 모두 건드리는 유스케이스는 애플리케이션 서비스(`CancelPointUseService`)가 한 트랜잭션에서 조율합니다.
- **핵심 도메인 로직**: 적립 lot의 소진 우선순위(관리자 지급 우선 → 만료 임박 순), 소진했던 순서 그대로 복원하는 부분 취소, 만료된 적립을 취소 복원할 때 신규 적립(`RESTORED_EXPIRED`)으로 처리하는 로직이 `PointAccount`/`PointEarn`/`PointUse` 안에 캡슐화되어 있습니다.
- **동시성**: 적립·사용·적립취소·사용취소 등 계정을 변경하는 모든 경로가 계정 조회 시 비관적 락(`@Lock(PESSIMISTIC_WRITE)`)을 사용합니다. 사용↔적립취소가 같은 적립건을 동시에 건드리는 경쟁 상태를 실제로 재현(둘 다 성공해 데이터가 깨지는 것을 확인)한 뒤 락으로 고쳤습니다.


## ERD

렌더링 도구(graphviz/mermaid-cli 등)가 설치되어 있지 않아 실제 JPA 엔티티를 기준으로 SVG를 직접 작성했습니다.

![ERD](./src/main/resources/erd.svg)

원본 파일: [`src/main/resources/erd.svg`](./src/main/resources/erd.svg)

`point_use_allocation.earn_point_key → point_earn.point_key`는 DB FK가 아니라 값 매칭입니다. `PointAccount`와 `PointUse`를 별도 애그리게잇으로 설계했기 때문에 일부러 FK로 묶지 않았습니다.

## AWS 아키텍처 (예시, 옵션)

실제 배포 환경을 가정했을 때의 구성 예시입니다. Route 53 → ALB → ECS Fargate(Spring Boot) → RDS(Multi-AZ) 형태이며, 과제 실행 환경의 H2는 운영 환경에서 RDS 등으로 교체가 필요합니다.

![AWS Architecture](./src/main/resources/aws-architecture.svg)

원본 파일: [`src/main/resources/aws-architecture.svg`](./src/main/resources/aws-architecture.svg)

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

자체 리뷰로 발견했던 결함(요청 검증 부재로 500 유출, 정책 자기모순 값 허용, 적립취소·사용취소 경로의 락 미적용)은 모두 수정했습니다 — 자세한 내용은 [`docs/개발작업내역.md`](./docs/개발작업내역.md)를 참고하세요.

## 테스트

```bash
./gradlew test
```

도메인 단위테스트부터 애플리케이션 서비스 테스트, `MockMvc` 기반 API 통합테스트(명세 4번 예시 시나리오 재현, 동시성 테스트, 입력 검증 포함)까지 총 55개 테스트로 구성되어 있습니다. 자세한 내역은 [`docs/개발작업내역.md`](./docs/개발작업내역.md)를 참고하세요.
