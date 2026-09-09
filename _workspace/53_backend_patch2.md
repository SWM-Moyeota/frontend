# 모여타 백엔드 배포 전 안전 패치 (리뷰 5건 적용)

- **대상 워크트리**: `…/scratchpad/backend-chatmember` (브랜치 `chore/chat-member-userid`, `origin/develop` = `294ad9c` 기준)
- **전제**: 커밋하지 않음. 워킹트리 변경만. 리더 패치 4파일(`ChatRoomUserService`, `ChatRoomMemberResult`, `FcmCallNotifier`, `PartyAccessService`)은 그대로 둔 채 그 위에 얹었다.
- **근거 리포트**: `_workspace/52_backend_review.md`
- **전체 diff**: `…/scratchpad/backend-prod-fixes.patch` (리더 4파일 변경 포함, 11파일 +139/-8)
- **경로 표기**: 모두 `src/main/java/team/codingforest/moyeota/` 기준
- **하지 않은 것**: 백엔드 프로세스 기동·에뮬레이터·실기기 없음. 컴파일 + 단위/슬라이스 테스트만.

---

## 적용 요약

| # | 항목 | 파일 | 성격 |
|---|------|------|------|
| 1 | H-1 AFTER_COMMIT 예외 격리 | `dispatch/application/DispatchListener.java` | try/catch 추가, **전파 변경 없음**(근거 아래) |
| 2 | H-5 방 생성 요청 검증 | `matching/interfaces/PartyController.java`, `matching/application/dto/OpenPartyRequest.java` | `@Valid` + `@NotNull`/`@NotBlank` |
| 3 | H-6 전역 예외 커버리지 | `common/exception/GlobalExceptionHandler.java`, `chat/presentation/ChatExceptionHandler.java` | 핸들러 5종 추가 + advice 순서 고정 |
| 4 | H-4 `@Scheduled` 전용 스케줄러 | `common/SchedulingConfig.java` (신규) | 빈 추가, `WebSocketConfig` 무변경 |
| 5 | H-2 join/leave 비관적 잠금 | `matching/application/PartyApplicationService.java` | `findById` → `findByIdForUpdate` |

---

## 1. H-1 — `DispatchListener` 예외 격리

**변경**: `on(MatchingStartedEvent)` 를 `try { dispatchService.dispatch(...) } catch (Exception e) { log.error("콜 디스패치 실패 partyId={}", …) }` 로 감쌌다. `@Slf4j` 추가. `ChatRoomMatchingListener` 와 동일한 모양이다.

**전파(propagation) 변경 여부 판단: 하지 않음.**

근거 — `DispatchService.attempt` 가 실제로 호출하는 것을 하나씩 확인했다.

| 호출 | 구현 | JPA 쓰기 여부 |
|------|------|----------------|
| `partyAccess.findSummary` | `PartyAccessService.findSummary` → `parties.findById` | 읽기만 |
| `driverLocations.findNearby` | `DriverLocationRedis` (Redis GEO) | JPA 아님 |
| `callCandidates.findAll/findRejected/add` | `CallCandidatesRedis` (Redis Set) | JPA 아님 |
| `driverAccess.canReceiveCalls` | `DriverAccessService` → `drivers.findById` | 읽기만 |
| `callNotifier.notifyCall` | `FcmCallNotifier` → `driverAccess.findFcmTokens`(읽기) + FCM 비동기 전송 | 읽기만 |

즉 `dispatch`/`attempt` 의 부작용은 **Redis 쓰기와 FCM 전송뿐**이고 `partyAccess.*` 쓰기 메서드(`assignDriver`/`failMatching`/`startRide`/`completeRide`)는 이 경로에서 호출되지 않는다. AFTER_COMMIT 에서 REQUIRED 트랜잭션이 이미 커밋된 트랜잭션에 참여해도 **유실될 JPA 쓰기가 없다** — `MatchingChatRoomService.provisionForParty` 가 `REQUIRES_NEW` 여야 했던 이유(채팅방·참여자 INSERT)가 여기엔 없다. 지침대로 "Redis/FCM 만이면 전파 변경 없음"에 해당해 `@Transactional` 을 그대로 뒀다.

**효과**: Redis 장애·FCM 예외·`PARTY_NOT_FOUND` 가 나도 `POST /matching/rooms`, `POST /matching/rooms/{id}/join` 이 200 으로 끝난다. 배차는 `MatchingSweeper` 가 30초마다 재시도한다.

---

## 2. H-5 — 방 생성 요청 검증

**변경**
- `PartyController.open` 파라미터에 `@Valid` 추가 (`jakarta.validation.Valid` import).
- `OpenPartyRequest` 필드: 좌표 4개 `@NotNull`, `departure`/`destination` `@NotBlank`, `capacity`/`departureRadius`/`destinationRadius` `@NotNull`.
- `creatorId` 는 토큰에서 오고 본문 값은 무시되므로(구버전 호환) 제약을 걸지 않았다.

**범위 판단**: 값 범위(`Capacity` 1~3, `Radius` 100~500m)는 도메인 VO 가 이미 `BusinessException`(`INVALID_CAPACITY`/`INVALID_RADIUS`, 400 + `{code,message}`)으로 지킨다. `@Min`/`@Max` 를 중복으로 걸면 같은 규칙이 두 군데 생기므로 **null 방어만** 추가했다(리뷰가 지목한 NPE/`DataIntegrityViolation` 500 경로가 이걸로 막힌다).

**다른 요청 DTO**: matching 모듈에 `JoinRequest` 는 없다(`join`/`leave` 는 `@PathVariable` + `@CurrentUser` 만 쓴다). 남은 것은 `RouteRequest` 인데 필드가 전부 primitive `double` 이라 `@NotNull` 을 걸 수 없고, 필드 누락 시 `HttpMessageNotReadableException` 이 나 **아래 3번(H-6)에서 400 `{code,message}` 로 처리된다.** 박싱 타입으로 바꾸는 건 시그니처 변경이라 "기계적·안전" 범위를 넘는다고 보고 손대지 않았다.

**핸들러 도달 확인**: `PartyController` 는 `matching` 패키지라 `ChatExceptionHandler(basePackages="…chat")` 대상이 아니다 → `GlobalExceptionHandler.handleValidation(MethodArgumentNotValidException)` 로 간다. 임시 MockMvc 테스트로 `{}` 본문 → 400 `INVALID_REQUEST` 를 실측 확인했다(테스트는 diff에 남기지 않음, 아래 "후속" 참고).

---

## 3. H-6 — 전역 예외 핸들러 보강

**`GlobalExceptionHandler` 에 추가한 핸들러** (응답은 기존과 같은 `common.exception.ErrorResponse` = `{code, message}`)

| 예외 | 상태 | code |
|------|------|------|
| `HttpMessageNotReadableException` | 400 | `INVALID_REQUEST` |
| `MissingServletRequestParameterException` | 400 | `INVALID_REQUEST` (`"{param}: 필수 값입니다."`) |
| `MethodArgumentTypeMismatchException` | 400 | `INVALID_REQUEST` (`"{name}: 값의 형식이 올바르지 않습니다."`) |
| `DataIntegrityViolationException` | 409 | `DUPLICATE_RESOURCE` |
| `Exception` (폴백) | 500 | `INTERNAL_ERROR` / `"일시적인 오류가 발생했어요"` + `log.error` 스택 |

**폴백이 삼키면 안 되는 것 처리** — 이게 이 항목에서 제일 위험한 부분이라 명시적으로 막았다.

```java
if(e instanceof org.springframework.web.ErrorResponse || e instanceof AccessDeniedException || e instanceof AuthenticationException) throw e;
```

- `ExceptionHandlerExceptionResolver` 는 `DefaultHandlerExceptionResolver` **보다 먼저** 돈다. 그래서 `@ExceptionHandler(Exception.class)` 를 그냥 두면 `NoResourceFoundException`(404), `HttpRequestMethodNotSupportedException`(405), `HttpMediaTypeNotSupportedException`(415) 같은 스프링 웹 예외가 전부 500으로 바뀐다. 이들은 모두 `org.springframework.web.ErrorResponse` 구현이라 다시 던져서 원래 처리로 흘려보낸다(핸들러가 원 예외를 재던지면 resolver 는 null 을 반환하고 기본 처리로 이어진다).
- `AccessDeniedException`/`AuthenticationException` 을 삼키면 시큐리티의 403/401 변환이 깨진다 → 역시 재던진다.
- `org.springframework.web.ErrorResponse` 는 같은 패키지의 응답 DTO `ErrorResponse` 와 이름이 겹쳐 **FQN 으로** 썼다(import 하면 패키지 멤버가 가려져 기존 코드가 전부 깨진다).

**chat 전용 핸들러와의 순서** — `ChatExceptionHandler` 에 `@Order(Ordered.LOWEST_PRECEDENCE - 100)`, `GlobalExceptionHandler` 에 `@Order(Ordered.LOWEST_PRECEDENCE)` 를 명시했다.
- 둘 다 순서 미지정이면 `LOWEST_PRECEDENCE` 동률이고, chat 컨트롤러에서 예외가 나면 **먼저 매칭된 advice 하나가 처리**한다. 새로 생긴 `Exception` 폴백이 `ChatException` 을 가로채면 채팅 에러코드가 전부 500 `INTERNAL_ERROR` 로 나갈 수 있었다.
- 기존에는 컴포넌트 스캔 순서(chat < common)에 우연히 의존하고 있었고, 이제 명시적으로 chat 이 앞선다. 범위(`basePackages`)는 그대로라 chat 이외 컨트롤러는 영향 없다.

---

## 4. H-4 — `@Scheduled` 전용 `TaskScheduler`

**변경**: `common/SchedulingConfig.java` 신규. `@Bean(name = "taskScheduler")` `ThreadPoolTaskScheduler`(poolSize 2, prefix `sched-`, `waitForTasksToCompleteOnShutdown`). `WebSocketConfig.heartbeatScheduler` 는 **무변경**. `initialize()` 는 수동 호출하지 않았다(컨테이너가 `afterPropertiesSet` 에서 부른다 — 수동+자동 이중 호출은 스레드풀을 하나 버린다).

**부팅 경고 소멸 실측** — 컨텍스트 로딩 테스트(`MoyeotaApplicationTests`) 로그를 파일 유무만 바꿔 두 번 비교했다.

- 파일 제거(= 현행 develop) 상태:
  ```
  INFO o.s.s.config.TaskSchedulerRouter : More than one TaskScheduler bean exists within the context,
  and none is named 'taskScheduler' … : [messageBrokerTaskScheduler, heartbeatScheduler]
  ```
- 파일 추가 상태: 위 로그 **0건**.

경고 원인이 `heartbeatScheduler` 단독이 아니라 `messageBrokerTaskScheduler` 와의 2개 공존이었다는 것도 이 로그로 확인됐다. 이름이 `taskScheduler` 인 빈이 생기면서 `TaskSchedulerRouter` 가 이름으로 해석에 성공하고, `MatchingSweeper` 의 30초 스윕이 STOMP 하트비트 풀(pool 1)을 더 이상 공유하지 않는다.

---

## 5. H-2 — join/leave 비관적 잠금

**변경**: `PartyApplicationService` 에 `getPartyForUpdate(partyId)`(= `parties.findByIdForUpdate`, `PESSIMISTIC_WRITE`) 를 추가하고 `join`/`leave` 가 이걸 쓰도록 했다. **포트 시그니처 무변경** — `Parties.findByIdForUpdate` 는 이미 있었고 `assignDriver`/`failMatching`/`startRide`/`completeRide` 가 쓰던 것과 같다. 읽기 전용 조회(`getPartyDetail` 등)는 `findById` 그대로.

**정원 초과·이벤트 2회 발행이 막히는 흐름**

1. 정원 3, 현재 2명인 방에 A·B 가 동시에 `join`.
2. 두 트랜잭션 모두 `validateNotInOngoingParty` 통과 후 `findByIdForUpdate(partyId)` 진입 → **`select … for update` 로 `match_room` 행에 배타 잠금**. A가 잡으면 B는 여기서 블록된다.
3. A: `party.join` → 3명 → `isFull()` → `startMatching()` → `MatchingStartedEvent` 발행 → `parties.save` → 커밋(이 시점에 AFTER_COMMIT 리스너가 배차/채팅방 1회 실행) → 잠금 해제.
4. B: 잠금을 얻고 **A의 커밋 결과가 반영된** 파티를 읽는다 → 이미 3명이므로 `Party.join` 의 정원 가드에 걸려 `BusinessException`(정원 초과) 으로 롤백. `startMatching()`/이벤트 2차 발행이 일어나지 않는다.

이전에는 2·4 단계에서 둘 다 "2명" 스냅샷을 읽어 `members.size() >= capacity` 를 통과, 정원 4명짜리 방 + `MatchingStartedEvent` 2회(콜 중복 발송, 채팅방 생성 2회 시도)가 가능했다. `leave` 도 같은 행을 만지므로 같은 잠금 아래로 넣어 join↔leave 교차 경합에서 멤버 목록이 어긋나는 것을 막았다.

`PartyMemberEntity` 복합 PK 는 서로 다른 유저의 INSERT 를 막지 못하므로 DB 제약으로는 해결되지 않는다 — 잠금이 유일한 수단이다.

---

## 테스트 결과

| 명령 | 결과 |
|------|------|
| `./gradlew compileJava compileTestJava -q` | **통과** (exit 0, 출력 없음) |
| `./gradlew test --tests '*Party*' --tests '*Dispatch*' --tests '*Exception*'` | **BUILD SUCCESSFUL** |
| `./gradlew test` (전체) | **BUILD SUCCESSFUL** — 42 스위트, failures=0, errors=0, skipped=0 |

전체 실행에 포함돼 통과한 것 중 이번 변경과 직접 맞물리는 것:
- `MoyeotaApplicationTests` (전체 컨텍스트 기동) — 새 `taskScheduler` 빈이 빈 충돌/기동 실패를 내지 않음을 확인.
- `ModularityTest` (Spring Modulith `verify()`) — `common` 에 설정 클래스를 추가해도 모듈 경계 위반 없음.
- `GlobalExceptionHandlerTest` (5건) — 기존 `{code,message}` 계약 유지.
- `PartyApplicationServiceTest` (24건) — 잠금 전환 후에도 통과(테스트 대역 `PartyJpaTest.findByIdForUpdate` 는 `findById` 위임).
- `ChatRoomAutoCreateIntegrationTest` — AFTER_COMMIT 채팅방 생성 회귀 테스트 통과.

**Redis/DB 가 필요해 실패한 통합 테스트는 없었다.** 이 저장소의 테스트는 H2 인메모리 + 페이크(`InMemoryCallCandidates`, `FakeDriverLocations` 등) 기반이라 외부 의존이 없다. 반대로 말하면 **실제 Redis·PostgreSQL·FCM 경로는 이번 검증 범위 밖**이다.

**추가 실측(임시 테스트, diff 에는 미포함)** — `PartyController` + `GlobalExceptionHandler` 를 standalone MockMvc 로 묶어 6건 확인 후 파일 삭제:
`{}` 본문 → 400 `INVALID_REQUEST` / 깨진 JSON `{` → 400 / `/matching/rooms/abc` → 400 / `@RequestParam` 누락 → 400 / 임의 `IllegalStateException` → 500 `INTERNAL_ERROR` + `"일시적인 오류가 발생했어요"` / `DELETE` 미지원 메서드 → **405 유지**(폴백이 안 삼킴).

---

## 적용하지 않은 것 (요청 범위 밖 / 판단)

- **H-1 전파 변경**: 위 근거로 미적용.
- **`RouteRequest` 박싱 전환**: primitive 시그니처 변경이라 제외. 누락 시 H-6 의 `HttpMessageNotReadable` 핸들러로 400 `{code,message}` 는 보장된다.
- **`Capacity`/`Radius` 범위의 `@Min`/`@Max` 중복 선언**: 도메인 VO 가 이미 담당.
- **리뷰의 나머지 항목**(H-3 유령 콜, H-7 채팅방 권한, H-8 prod 스키마, M-6 소유권 검증 등): 지시대로 손대지 않음. **H-8(스키마 대조)은 여전히 배포 전 1순위**이고, 이번 패치로 해결되지 않는다.

## 후속 제안 (선택)

1. 위 임시 MockMvc 6건을 `GlobalExceptionHandlerTest` 에 정식으로 넣으면 폴백이 404/405/403 을 삼키는 회귀를 CI 가 잡는다. 원하면 바로 추가하겠다.
2. `DispatchListener` 예외 격리에 대응하는 회귀 테스트가 없다(`ChatRoomAutoCreateIntegrationTest` 의 배차 버전). 리뷰 관찰 16번이 지적한 공백 그대로다.
