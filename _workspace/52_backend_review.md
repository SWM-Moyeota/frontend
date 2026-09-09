# 모여타 백엔드 코드 리뷰 (읽기 전용)

- **대상**: `origin/develop` (`294ad9c`) + 리더 패치 4파일
  - `PartyAccessService` 클래스 레벨 `@Transactional(readOnly)`
  - `ChatRoomMemberResult.userId` 추가
  - `FcmCallNotifier` memberCount/estimatedFare 데이터 추가
- **스택**: Spring Boot 4.1.0 / Java 25 / Spring Modulith 2.0.1 / JPA·Hibernate / Redis / FCM / STOMP
- **방법**: 정적 읽기 리뷰(빌드·실행·테스트 없음). 파일 수정 없음.
- **경로 표기**: 모두 `src/main/java/team/codingforest/moyeota/` 기준

---

## 배포 전 반드시 고칠 것 TOP 5

| # | 항목 | 왜 지금인가 |
|---|------|-------------|
| 1 | **H-8** prod 스키마 대조 (`ddl-auto: validate` + 마이그레이션 도구 부재) | 최근 커밋이 테이블명·컬럼을 계속 바꿨는데 DDL 반영 수단이 없다. 어긋나면 **기동 자체가 실패**한다. |
| 2 | **H-1** `DispatchListener` 예외 미처리 | AFTER_COMMIT 예외가 커밋 밖으로 새어 **방 생성/합류가 DB엔 성공했는데 앱엔 500**으로 보인다. 이미 겪은 계열의 두 번째 함정. |
| 3 | **H-7** 채팅방 권한 검사 부재 | 로그인만 하면 **남의 채팅방을 읽고, 들어가고, 종료(CLOSED)** 시킬 수 있다. |
| 4 | **H-6 + H-5** 예외 폴백 + `@Valid` 누락 | 방 생성에 필드 하나만 빠져도 NPE 500이고, 본문이 `{code,message}`가 아니라 앱이 파싱을 못 한다. |
| 5 | **H-2** join 비관적 잠금 누락 | 동시 합류 시 **정원 초과 + 매칭 이벤트 2회 발행**(콜 중복 발송). |

이어서 H-3(유령 콜), H-4(스케줄러 공유로 WS 하트비트 끊김), M-7/M-8(네이버 키·Redis 호스트) 순.

---

## 심각도별 표

### 높음

| 항목 | 파일:라인 | 근거 | 영향 | 수정 제안 | 확신도 |
|------|-----------|------|------|-----------|--------|
| **H-1** AFTER_COMMIT 리스너 예외가 API 응답을 500으로 만든다 | `dispatch/application/DispatchListener.java:15-18` | `on()` 에 try/catch가 없다. 같은 이벤트를 받는 `chat/app/ChatRoomMatchingListener.java:21-33` 은 `ChatException`/`Exception` 을 전부 삼킨다. Spring 은 AFTER_COMMIT 동기화에서 던져진 예외를 `PlatformTransactionManager.commit()` 밖으로 **재던진다**(`TransactionalApplicationListenerSynchronization` → `processCommit`). | Redis 장애·FCM 예외·`DispatchErrorCode.PARTY_NOT_FOUND` 가 나면 `POST /api/v1/matching/rooms`, `POST /api/v1/matching/rooms/{id}/join` 이 **500**. 그런데 파티 생성/합류는 이미 커밋됐다 → 앱은 "실패"로 보고 재시도, 서버엔 `ALREADY_JOINED_OTHER_PARTY` 로 막힌 유령 방이 남는다. | 채팅 리스너와 동일하게 `try { … } catch (Exception e) { log.error(…); }`. 배차는 스윕(30초)이 어차피 재시도한다. | 높음 |
| **H-2** join/leave 정원 경합 (비관적 잠금 미적용) | `matching/application/PartyApplicationService.java:67-83`(join), `86-93`(leave) / `matching/infrastructure/PartyJpaRepository.java:21-23` | 상태 변경 메서드 중 `assignDriver`/`failMatching`/`startRide`/`completeRide` 만 `findByIdForUpdate`(PESSIMISTIC_WRITE)를 쓴다. join/leave 는 잠금 없는 `parties.findById`. `PartyMemberEntity` PK 가 (match_id, user_id) 복합키라 서로 다른 유저의 동시 INSERT 는 충돌하지 않는다. | 정원 2 남은 1자리에 두 명이 동시에 들어오면 둘 다 `members.size() >= capacity` 를 통과 → **정원 초과 방**. 게다가 둘 다 `isFull()` → `startMatching()` → **`MatchingStartedEvent` 2회 발행** → 콜 2회 발송, 채팅방 생성 2회 시도(`chat_room.party_id` unique 로만 겨우 방어). | join/leave 도 `parties.findByIdForUpdate(partyId)` 사용. | 높음 |
| **H-3** `attempt()` 상태 미검증 → 이미 배정된 방에 유령 콜 | `dispatch/application/DispatchService.java:81-105` / `dispatch/application/MatchingSweeper.java:31-43` | `attempt` 는 `partyAccess.findSummary` 로 좌표만 읽고 파티 상태를 확인하지 않는다. 스윕은 루프 시작 시점의 `findMatchingTargets()` 스냅샷으로 순회한다. | 순회 중 다른 방이 수락되면(`acceptCall` 이 `callCandidates.clear`) 그 뒤 `attempt` 가 **후보를 다시 채워 넣고 CALL_OPENED FCM 을 쏜다**. 기사가 수락하면 `contains`=true 로 통과했다가 `Party.assignDriver` 에서 `PARTY_NOT_MATCHING`/`DRIVER_ALREADY_ASSIGNED` 409. 기사 앱엔 "잡았는데 사라진 콜". | `PartyAccess` 에 `isMatching(partyId)` 추가하고 `attempt` 진입부에서 아니면 즉시 return. `acceptCall` 이후 `dispatch:candidates:*` 재생성 방지. | 높음 |
| **H-4** `MatchingSweeper` 가 STOMP 하트비트 스케줄러를 공유한다 | `chat/config/WebSocketConfig.java:43-50` / `dispatch/application/MatchingSweeper.java:29` | `heartbeatScheduler()` 가 컨텍스트의 **유일한 `TaskScheduler` 빈**이다. Boot 의 `TaskSchedulingAutoConfiguration` 은 `@ConditionalOnMissingBean(TaskScheduler)` 라 백오프하고, `ScheduledAnnotationBeanPostProcessor` 는 타입으로 유일 빈을 찾아 그걸 쓴다. 풀 사이즈 1. | 30초 스윕(방 수 × DB 조회 + Redis GEO + `canReceiveCalls` N+1 + FCM)이 도는 동안 **SimpleBroker 의 10초 하트비트가 밀린다** → 채팅 클라이언트가 연결을 끊는다. 스윕이 예외로 멈추면 하트비트도 같이 지연. | 스윕 전용 `TaskScheduler` 를 `@Bean("taskScheduler")` 로 따로 두거나(풀 2 이상), WS 스케줄러를 `registry.setTaskScheduler(...)` 로만 쓰고 빈 노출을 피한다. | 높음 (메커니즘 확신, 실측은 안 함) |
| **H-5** 방 생성 요청에 `@Valid` 없음 → 필드 누락이 NPE 500 | `matching/interfaces/PartyController.java:22-27` / `matching/application/dto/OpenPartyRequest.java:3-5` | `@RequestBody OpenPartyRequest request` 에 `@Valid` 가 없고, 레코드는 전부 박싱 타입에 제약 애노테이션이 하나도 없다. `PartyApplicationService.open` 은 `new Location(command.departureLat(), …)`(double 파라미터), `new Capacity(command.capacity())`(int) 로 즉시 언박싱한다. | `capacity`/좌표/반경 중 하나만 빠져도 **NullPointerException → 스프링 기본 500**(`{timestamp,status,error,path}`). `departure`/`destination` null 은 `@Column(nullable=false)` 위반 → `DataIntegrityViolationException` → 역시 500. 앱은 `{code,message}` 파싱 실패. | `@Valid` + 레코드에 `@NotNull`/`@Min`/`@Max`. 같은 문제가 `POST /matching/routes`(`RouteRequest`, primitive 라 null 이면 `HttpMessageNotReadable`)에도 있다. | 높음 |
| **H-6** `@RestControllerAdvice` 커버리지 부족 → 500/400 이 규격 밖 본문으로 샌다 | `common/exception/GlobalExceptionHandler.java:14-54` | 핸들러가 `BusinessException`, `IllegalArgumentException`, `MethodArgumentNotValidException` 셋뿐. `Exception` 폴백이 없다. `chat/presentation/ChatExceptionHandler.java` 는 chat 패키지 한정 `ChatException`+검증만. | 아래 전부가 스프링 기본 본문으로 나간다 → 앱 파싱 실패:<br>· `HttpMessageNotReadableException` (JSON 깨짐, enum 오타 `gender:"X"`)<br>· `MissingServletRequestParameterException` (`GET /matching/rooms?swLat=…` 누락, `GET /places` query 누락, `GET …/messages/after` cursor 누락)<br>· `MethodArgumentTypeMismatchException` (`/matching/rooms/abc`, `size=abc`)<br>· `DataIntegrityViolationException` (M-4 중복 가입 경합)<br>· `LazyInitializationException` / NPE / 그 외 전부<br>· `AccessDeniedException` | `GlobalExceptionHandler` 에 위 6종 + `@ExceptionHandler(Exception.class)` 폴백(500, `INTERNAL_ERROR`) 추가. `ResponseEntityExceptionHandler` 상속으로 한 번에 잡아도 된다. | 높음 |
| **H-7** 채팅방 권한 검사 부재 (4곳) | `chat/presentation/ChatRoomController.java:20-24`, `26-32`, `34-40` / `chat/presentation/ChatRoomUserController.java:26-33` → `chat/app/ChatRoomUserService.java:31-53` | · `getChatRoom` 은 `@CurrentUser Long userId` 를 **받아만 두고 쓰지 않는다**.<br>· `deleteChatRoom` 은 참여자 검증 없이 `chatRoomService.close(chatRoomId)`.<br>· `join` 은 방 상태(`validateJoin`)와 중복만 보고 **파티 멤버십을 보지 않는다**.<br>· `createChatRoom` 은 임의 partyId 로 방 생성 가능. | 로그인한 아무나 chatRoomId 를 1부터 훑어:<br>· 남의 방 출발/도착지 열람<br>· **남의 방을 CLOSED 로 만들어 대화 차단(DoS)** — `ChatRoom.close()` 는 ACTIVE면 무조건 통과<br>· 남의 방에 입장 후 전체 메시지 열람·전송(`validateParticipant` 를 통과하게 된다) | `getChatRoom`/`deleteChatRoom` 에 `chatRoomUserService.validateParticipant(userId, chatRoomId)`. `join` 은 방의 partyId 로 `partyAccess.hasMemberOnParty(userId, partyId)` 검증. `create`/`close` 는 자동 생성 경로 전용으로 내리고 REST 노출 제거 권장. | 높음 |
| **H-8** prod 스키마 마이그레이션 도구 부재 + `ddl-auto: validate` | `src/main/resources/application-prod.yaml:9-12` / `build.gradle` / 저장소 전체 | Flyway·Liquibase 의존성 없음. `src/main/resources/db/migration` 없음(있는 SQL 은 벤치마크용 `docs/evidence/seed.sql` 뿐). 로컬은 H2 in-mem + `ddl-auto` 미지정(기본 create-drop)이라 **스키마가 검증된 적이 한 번도 없다**. 최근 스키마 변경 커밋: `5c3909d`(user→**users** 테이블명), `e46cec3`(`match_room.match_started_at`), `adc20dd`(`users.fcm_token`), `1bc1023`(`users.nickname` unique), `0594061`, `ee516e0`/`087fa7d`(`refresh_token` jti PK 구조 변경). | prod DB 에 대응 DDL 이 손으로 반영돼 있지 않으면 `SchemaManagementException` 으로 **애플리케이션이 기동 실패**. `validate` 는 컬럼 타입·nullable·길이까지 본다. 특히 `users` 테이블명 변경은 전면 실패. | 배포 전 prod 스키마를 엔티티와 1:1 대조(부록 A 목록). 중기적으로 Flyway 도입 + `validate` 유지. | 높음 |

### 중간

| 항목 | 파일:라인 | 근거 | 영향 | 수정 제안 | 확신도 |
|------|-----------|------|------|-----------|--------|
| **M-1** AFTER_COMMIT → `@Transactional`(REQUIRED) 잔존 | `dispatch/application/DispatchListener.java:17` → `dispatch/application/DispatchService.java:39-42` | AFTER_COMMIT 시점엔 `EntityManagerHolder` 가 아직 스레드에 바인딩돼 있어 `isExistingTransaction()`=true → REQUIRED 가 **끝난 트랜잭션에 참여**한다. 채팅방 자동 생성 때 겪은 그 함정과 동일 구조. | **지금은 무해**: `dispatch`→`attempt` 경로는 파티 조회(읽기) + Redis(`CallCandidatesRedis`, `DriverLocationRedis`) + FCM 뿐, JPA 쓰기가 없다. 다만 여기에 쓰기가 하나라도 들어오면(예: "콜 발송 이력 저장") **조용히 유실**된다. | `MatchingChatRoomService` 처럼 `@Transactional(propagation = REQUIRES_NEW)` 로 못 박기. 주석으로 이유 남기기. | 높음 (현재 무해 판정 포함) |
| **M-2** 스윕의 catch 범위가 좁다 | `dispatch/application/MatchingSweeper.java:44` | `catch (BusinessException \| IllegalArgumentException e)`. Redis 장애(`RedisConnectionFailureException`), FCM 예외, `ChatException`, NPE 는 안 잡힌다. | 한 방에서 Redis 예외가 나면 **그 회차의 나머지 방이 전부 스킵**된다(30초 뒤 재시도이긴 하나 타임아웃 해산·반경 확대가 밀린다). `@Scheduled` 는 예외를 로그만 찍고 다음 주기로 넘어가므로 무한 정지는 아님. | `catch (Exception e)` 로 넓히고 방 단위로 continue. | 중 |
| **M-3** `DriverAccessService` 무트랜잭션 N+1 | `driver/application/DriverAccessService.java:18`(클래스에 `@Transactional` 0개), `23-27`, `30-39` | `findFcmTokens` 가 driverIds 를 루프 돌며 `drivers.findById` 를 건당 호출. `DriverEntity.vehicle`/`setting` 은 `@OneToOne(mappedBy)` = JPA 기본 EAGER 라 건당 3쿼리. `DispatchService.attempt:93` 의 `.filter(driverAccess::canReceiveCalls)` 도 후보 수만큼 같은 3쿼리. 클래스에 트랜잭션이 없어 **호출마다 별도 트랜잭션 + 커넥션 획득**. | 후보 50명이면 콜 1회에 ~300쿼리 / 커넥션 획득 100회. 풀은 `DB_POOL_MAX_SIZE:10`. 30초마다 반복. | 클래스에 `@Transactional(readOnly = true)`, `drivers.findAllById(ids)` 배치 조회 추가. FCM 토큰은 `@Query` 로 driverId+token 만 뽑기. | 높음 |
| **M-4** 중복 가입 경합이 500 | `user/application/LocalUserService.java:22-44` | loginId/phone/nickname 을 check-then-insert. unique 제약은 `local_user.login_id`, `user_profile.phone_number`, `users.nickname`(`UserEntity.java:23`). | 동시 요청이면 `DataIntegrityViolationException` → H-6 때문에 핸들러 없음 → **500**. 앱은 "이미 있는 아이디"를 구분 못 한다. | `DataIntegrityViolationException` 핸들러로 409 매핑(제약 이름으로 USER101/104/108 분기) 또는 catch 후 도메인 예외로 변환. | 높음 |
| **M-5** 재입장 시 `leftAt` 이 조용히 덮인다 / dead catch | `chat/app/ChatRoomUserService.java:45-49` / `chat/infra/entity/ChatRoomUserEntity.java:15-19` | PK 가 (userId, chatRoomId) 복합키 → `save` 는 INSERT 가 아니라 **merge**. 따라서 `DataIntegrityViolationException` 은 발생하지 않는다(죽은 catch). 나갔던 유저가 다시 join 하면 같은 행의 `leftAt` 이 null 로 덮인다. | 나간 이력이 사라진다. 의도라면 OK, 아니라면 나간 유저의 재입장을 막을 방법이 없다. | 의도 확인 후, 이력 보존이 필요하면 대리키 + (userId, chatRoomId, leftAt) 인덱스로 변경. dead catch 제거. | 중 |
| **M-6** 파티 상세·기사 정보 소유권 검증 없음 | `matching/interfaces/PartyController.java:41-44`(detail), `56-59`(driver), `46-49`(list), `62-68`(listWithin) | 네 엔드포인트 모두 `@CurrentUser` 자체가 없다. 인증은 필요하다(`SecurityConfig.java:41` `anyRequest().authenticated()`). | 임의 partyId 로 **동승자 publicId·닉네임·프로필 이미지·탑승 횟수**(`PartyDetailResult.MemberInfo`)와 **배정 기사 차량번호·차종·좌석수**(`DriverSummary`)를 조회 가능. | `listWithin`/`list` 는 지도용 공개 조회로 의도된 것으로 보이나(응답에 개인정보 없음 — `PartyResult`), `detail`·`driver` 는 `@CurrentUser` + `hasMemberOnParty` 검증 추가. | 높음 |
| **M-7** prod 네이버 키 기본값이 빈 문자열 | `src/main/resources/application-prod.yaml:22-25` vs `18-20` | `naver.api.key-id: ${NAVER_API_KEY_ID:}`, `key: ${NAVER_API_KEY:}` — 기본값 빈 문자열. `kakao.api.key: ${KAKAO_API_KEY}` 는 기본값 없음(미설정 시 기동 실패). 정책이 엇갈린다. | 네이버 키 미설정이면 `NaverApiConfig.java:14-18` 이 빈 헤더로 클라이언트를 만들고 → 네이버 401 → `RestClientException` → `MatchingErrorCode.ROUTE_SEARCH_FAILED`(**502**). **방 생성·경로 미리보기가 전부 502**, 기동은 정상이라 헬스체크는 통과한다(가장 나쁜 실패 모드). | 네이버도 기본값 제거해서 fail-fast 로 통일. 최소한 기동 로그에 키 존재 여부를 남길 것. | 높음 |
| **M-8** prod Redis 호스트 하드코딩 | `src/main/resources/application-prod.yaml:13-16` | `host: localhost`, 환경변수 오버라이드 없음. 배포는 EC2 + CodeDeploy(`appspec.yml`, `scripts/start.sh` → systemd). | EC2 같은 인스턴스에 Redis 가 떠 있다면 정상이지만(**근거는 `docker-compose.yml` 뿐 — 추측**), ElastiCache/별도 인스턴스로 옮기는 순간 콜 후보·기사 위치·채팅 브로드캐스트·경로 캐시가 전부 죽는다. Lettuce 는 지연 연결이라 **기동은 성공하고 런타임에만 터진다**. | `${REDIS_HOST:localhost}` / `${REDIS_PORT:6379}` 로 분리. 인프라 실제 배치 확인 필요. | 중 (인프라 미확인) |
| **M-9** `completeRide` 요금 미저장 | `matching/domain/Party.java:185-192`(`// TODO 결제쪽이 완료된 후 완성`) / `matching/infrastructure/PartyEntity.java:124-129` | 도메인이 fare 를 필드로 갖지 않고, `PartyEntity.update` 도 status/taxiDriverId/matchingStartedAt 만 반영. `PartyEntity` 에 실제 요금 컬럼 자체가 없다(`estimated_fare` 는 예상치). | `POST /dispatch/rides/{id}/complete` 로 넘긴 실제 요금이 **어디에도 남지 않는다**. 정산·이력 화면 불가. | 알려진 미해결. `match_room.actual_fare` 추가 + `Party.completeRide` 저장. | 확인 완료 |

### 낮음

| 항목 | 파일:라인 | 내용 |
|------|-----------|------|
| **L-1** `badgeId` 타입 불일치 + 미연결 | `matching/application/dto/PartyDetailResult.java:21, 42-47` / `user/api/MemberSummary.java:5` | `MemberInfo.badgeId` 는 `String` 인데 `toMemberInfo` 가 항상 `null` 을 넣는다(`// TODO badgeId 값 설정`). 원천인 `MemberSummary.badgeId` 는 `Long`. **알려진 항목, 확인함.** |
| **L-2** 미인증 401 이 `USER005` | `user/domain/exception/UserErrorCode.java:11` / `user/interfaces/auth/JsonAuthenticationEntryPoint.java:29` / `user/interfaces/auth/CurrentUserArgumentResolver.java:34` | REST 채팅 API 의 401 도 `USER005`. `CHAT_UNAUTHORIZED` 는 STOMP 인터셉터에서만 쓰인다. 앱은 두 코드를 모두 처리해야 함. **확인함.** |
| **L-3** 닉네임 오류 코드 이원화 | `user/application/dto/NicknameCheckRequest.java:5` / `user/domain/Nickname.java:8-12` / `user/application/dto/UserRegisterRequest.java:20-22` | 공백/누락 → `INVALID_REQUEST`(bean validation), 형식 위반 → `USER107`. `UserRegisterRequest.nickname` 은 `@Size(2,10)` 만 있고 `@Pattern` 이 없어 이모지·특수문자는 서비스 계층 `USER107` 로 빠진다. **확인함.** |
| **L-4** 내부 PK 노출 | `chat/app/dto/ChatMessageResult.java:11` / `chat/app/dto/ChatRoomMemberResult.java:6-7`(이번 패치) | 메시지↔멤버 연결을 위한 의도적 선택. 장기적으로는 메시지에도 `publicId` 를 실어 내부 PK 를 감추는 방향. **확인함.** |
| **L-5** `GET /matching/rooms/me` 부재 · 범위 조회에 내 방 포함 | `matching/interfaces/PartyController.java` 전체 / `matching/application/PartyApplicationService.java:105-109, 127-133` | 내가 참여 중인 방을 서버에 물어볼 수단이 없다. `list`/`listWithin` 은 `PartyStatus.ACTIVE` 전부를 반환하므로 내가 이미 들어간 방(정원 미달이면 여전히 ACTIVE)도 섞여 나온다. **확인함.** |
| **L-6** STOMP 인터셉터 예외가 규격 밖으로 나간다 | `chat/config/StompAuthInterceptor.java:34-62` / `chat/presentation/StompExceptionHandler.java` | `preSend` 에서 던진 `ChatException` 은 `@MessageExceptionHandler` 대상이 아니다(핸들러 메서드 밖). `/user/queue/errors` 로 `{code,message}` 가 안 가고 STOMP ERROR 프레임 + 끊김. 앱이 CONNECT/SUBSCRIBE 실패 사유를 코드로 구분 불가. |
| **L-7** 죽은 회원가입 엔드포인트 | `user/interfaces/LocalUserController.java:21-24` / `user/interfaces/auth/SecurityConfig.java:40` | `POST /api/v1/local/users` 는 permitAll 목록(`/api/v1/auth/**`, `/ws-chat/**`, `/health`)에 없어 **토큰이 있어야 호출된다** → 사실상 호출 불가. 실제 가입은 `POST /api/v1/auth/register`. **가입 우회 구멍은 아니다.** 중복 엔드포인트 제거 권장. |
| **L-8** 기사 셀프 승인 | `driver/interfaces/DriverController.java:18-24` | 주석에 PoC 라고 명시. 기사가 본인 자격을 `VERIFIED` 로 만든다 → 즉시 콜 수신 가능. 운영 전 admin 권한 필요. |
| **L-9** 로컬 시크릿이 jar 에 실릴 수 있다 | `src/main/resources/application.yaml:5` | `spring.config.import: optional:classpath:application-local.yaml` 는 prod 프로필에서도 적용된다. `application-local.yaml` 에는 카카오/네이버/포트원 키와 JWT secret 이 평문. gitignore 라 저장소엔 없고 CI(`deploy.yml`)는 clean checkout 이라 현재 배포물엔 없지만, **개발자 로컬에서 만든 jar 를 수동 배포하면 시크릿이 아티팩트에 실린다.** |
| **L-10** 엔티티에 `@EnableJpaAuditing` | `place/infrastructure/FavoritePlaceEntity.java:16` | `common/JpaAuditingConfig` 와 중복. 동작엔 영향 없으나 제거. |
| **L-11** `WS_ALLOWED_ORIGINS` 필수값 | `src/main/resources/application-prod.yaml:29` / `chat/config/WebSocketConfig.java:21-22` | 기본값 없음 → 미설정이면 `@Value` 해석 실패로 기동 실패. 의도된 fail-fast 라면 배포 체크리스트에 명시. `String[]` 주입이라 콤마 구분. |
| **L-12** 검증 전에 외부 API 를 부른다 | `matching/application/PartyApplicationService.java:37-46` | `estimateRoute`(네이버 호출)가 `Capacity`/`Radius`/`Location` 검증보다 먼저 실행된다. 잘못된 capacity 요청도 네이버 쿼터를 태운다. |
| **L-13** `acceptCall` 의 Redis 정리가 트랜잭션과 어긋난다 | `dispatch/application/DispatchService.java:59-66` | `callCandidates.clear` / `driverLocations.remove` 는 트랜잭션 밖 자원이라, 뒤에서 롤백이 나도 되돌아오지 않는다. 영향은 후보 목록·기사 위치 유실뿐(다음 하트비트로 복구). |

---

## 확인했지만 문제 없음

각 항목마다 **무엇을 봤는지**를 남긴다.

1. **FirebaseConfig 스킵 시 콜 알림 NPE — 없다.**
   `dispatch/infrastructure/FirebaseConfig.java:17`, `FcmCallNotifier.java:25`, `FcmPassengerNotifier.java:26` 이 모두 `@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")` 로 묶여 있고, `LoggingCallNotifier.java` / `LoggingPassengerNotifier.java` 는 조건 없이 등록된다. FCM 쪽이 `@Primary` 라 켜져 있을 땐 FCM, 꺼져 있으면 로그 폴백. **주입 실패도 NPE 도 없다.** (다만 prod 에서 `FCM_SERVICE_ACCOUNT_PATH` 를 안 넣으면 푸시가 조용히 로그로만 나간다 — 배포 체크리스트에 넣을 것.)

2. **리더 패치 3건 모두 정합하고, 의도한 구멍을 실제로 막았다.**
   `matching/application/PartyAccessService.java:20` 의 클래스 레벨 `@Transactional(readOnly = true)` 로 `findSummary`(27), `findChatSummary`(83), `hasMemberOnParty`(101), `isRidingMember`(113), `findMemberIds`(120), `isAwaitingPickup`(76), `findMatchingTargets`(53), `hasOngoingRide`(96) 가 전부 트랜잭션 안으로 들어왔다. 무트랜잭션 호출자를 전수 확인했다 — `DispatchService.getDetailRoom`(112-117)·`isCallOpen`(119-121), `RideService.arrive`(27-33)·`driverLocation`(58-70), `FcmPassengerNotifier.notifyDriverArrived`(34-35), `MatchingPartyProvider.findSnapshot`(20-22), `MatchingSweeper.sweep`(31), `ReportApplicationService.report`(26,31) — 모두 커버된다. 쓰기 메서드(32,42,57,66)의 메서드 레벨 `@Transactional` 이 클래스 레벨보다 우선하는 것도 맞다.

3. **패치로 깨지는 호출부/테스트 없다.**
   `grep ChatRoomMemberResult` → `ChatRoomUserService`(생성 1곳), `ChatRoomUserController`(반환 타입)뿐, 테스트에서 생성하는 곳 없음. `FcmCallNotifier` 가 새로 넣은 `party.memberCount()`/`party.estimatedFare()` 는 `matching/api/PartySummary.java:3` 에 실제로 존재하는 컴포넌트(각각 8번째, 9번째)이고, `FcmCallNotifierTest` 는 `putData` 내용을 검증하지 않는다(토큰 없을 때 전송 안 함만 검증). `estimatedFare` null 가드도 들어가 있다.

4. **다른 지연 로딩 구멍은 없다 (open-in-view=false 전제로 전수 확인).**
   엔티티 전체에서 연관관계는 6개뿐:
   - `PartyEntity.members` `@OneToMany`(기본 LAZY) — **이번 패치로 해결된 그 지점**. `toDomain():121` 에서 접근.
   - `PartyMemberEntity.party` `@ManyToOne(LAZY)` — `toDomain():40-42` 이 역참조하지 않음.
   - `VehicleEntity.driver`, `DriverSettingEntity.driver` `@OneToOne(LAZY)` — 둘 다 `toDomain()` 이 역참조하지 않음.
   - `DriverEntity.vehicle`, `DriverEntity.setting` `@OneToOne(mappedBy)` — **fetch 미지정 = JPA 기본 EAGER**. Spring Data 리포지터리 메서드가 자체 트랜잭션을 열므로 `findById` 반환 시점에 이미 초기화돼 있다. 그래서 `DriverAccessService`(무트랜잭션)와 `CurrentDriverArgumentResolver`(컨트롤러 트랜잭션 밖에서 실행)의 `DriverEntity::toDomain` 도 **안전**하다.
   chat / user / report / place 엔티티는 연관관계가 아예 없다(전부 ID 참조). 따라서 `LocalUserService.getProfile`·`authenticate`, `PlaceSearchApplicationService`, `TokenAuthenticatorService` 같은 무트랜잭션 메서드도 안전.

5. **다른 AFTER_COMMIT 리스너에 JPA 쓰기 유실 위험 없다.**
   전수: `chat/app/ChatRoomMatchingListener.java:21`(→ `MatchingChatRoomService.provisionForParty` 가 `REQUIRES_NEW`, 33행 — 올바름), `chat/presentation/ChatMessageBroadcaster.java:26,31,36`(Redis `convertAndSend` 만, JPA 접근 없음), `dispatch/application/DispatchListener.java:15`(M-1 로 별도 기재, 현재는 읽기+Redis+FCM 뿐). `@Async` 사용처 없음. `@Scheduled` 는 `MatchingSweeper.sweep` 하나.

6. **콜 수락 경합은 DB 쪽이 안전하다.**
   `DispatchService.acceptCall:57` → `PartyAccessService.assignDriver:32-40` → `getForUpdate` → `PartyJpaRepository.findByForUpdate`(`@Lock(PESSIMISTIC_WRITE)`, 21-23). `acceptCall` 이 `@Transactional` 이라 잠금이 커밋까지 유지된다. 그 안에서 `Party.assignDriver`(`Party.java:162-169`)가 `status != MATCHING` 또는 `taxiDriverId != null` 이면 던진다. 두 기사가 동시에 눌러도 두 번째는 409(`DRIVER_ALREADY_ASSIGNED`). Redis 후보 제거가 원자적이지 않아도 **DB 가 최종 방어선으로 동작한다.** (남는 문제는 H-3 의 재생성 경로.)

7. **하트비트 TTL 이 일관적이다.**
   `dispatch/infrastructure/DriverLocationRedis.java:26`(TTL 30초), `update:35`(갱신), `findNearby:54`(`.filter(this::isAlive)`), `getLocationDriver:60`(같은 `isAlive` 가드), `remove:41`. 조회 두 경로가 같은 생존 기준을 쓴다. 기사 앱이 30초 이내로 `POST /api/v1/dispatch/location` 을 보내야 후보에 남는다는 계약이 코드와 일치.

8. **STOMP SUBSCRIBE 목적지 검증은 제대로 돼 있다.**
   `chat/config/StompAuthInterceptor.java:74-108` — `/sub/chat-rooms/{id}` 외 목적지는 거부(`parseChatRoomId` null → `CHAT_ROOM_NOT_FOUND`), 방 구독은 `findActiveByUserIdAndChatRoomId` 로 **활성 참여자만** 통과, `/user/queue/errors`·`/user/queue/room-left` 만 예외 허용. SEND 는 `/pub/` 프리픽스 강제(88-92). 브로커는 `/sub`,`/queue` 만 오픈(`WebSocketConfig.java:33`). CONNECT 는 Bearer 토큰 필수(110-118). → REST 쪽 구멍(H-7)과 달리 **WS 구독 경로는 막혀 있다.**

9. **액추에이터·H2 콘솔 노출 없다.**
   `application.yaml:42-50` — `exposure.include: health` 하나, `show-details: never`, base-path `/` → `/health` 만 열린다. `SecurityConfig.java:40` 에서 permitAll. H2 콘솔 설정 자체가 없다(`spring.h2.console` 없음). CSRF/폼로그인/basic 전부 disable + STATELESS 세션 — JWT 전용으로 일관.

10. **로그에 토큰·개인정보 없다.**
    전 서비스 로그 문자열을 훑었다. `RefreshTokenService.java:41` 은 jti(UUID)만, `UserProfileService.java:37` 은 변경 여부 boolean 만, `UserFcmTokenService.java:27,37` 은 userId 만, `JwtAuthenticationFilter`/`AuthController` 는 로그 없음. access/refresh 토큰 원문·비밀번호·전화번호·이메일을 찍는 곳 없음. `LoggingCallNotifier.java:20` 이 출발/도착지명을 찍는 정도(운영에선 FCM 쪽이 `@Primary` 라 실행 안 됨).

11. **Postgres 예약어 충돌 없다.**
    전체 `@Table` 목록: `users`(← `user` 회피, `UserEntity.java:15` 주석대로), `user_profile`, `local_user`, `refresh_token`, `match_room`, `user_match_room`, `taxi`, `taxi_driver`, `driver_setting`, `chat_room`, `chat_room_user`, `chat_message`, `driver_report`, `favorite_place`. 예약어에 걸리는 이름 없음. 컬럼 쪽도 `status`, `type`, `content` 등은 Postgres 비예약어.

12. **`Instant`/`UUID`/enum 매핑 자체는 정상.**
    Hibernate 6 는 `Instant` → `timestamp(6) with time zone`, `UUID` → `uuid`(Postgres 네이티브)로 매핑한다. enum 은 **전부** `@Enumerated(EnumType.STRING)` — `PartyStatus`, `ChatRoomStatus`, `ChatMessageType`, `ChatMessageStatus`, `LoginType`, `Gender`, `DriverStatus`, `ReportStatus` 확인. `@Enumerated` 누락(= 기본 ORDINAL)인 곳 없음. **다만 이 매핑이 실제 prod 스키마와 맞는지는 H-8 로 별도 검증 필요** — 로컬 H2 는 create-drop 이라 검증된 적이 없다.

13. **`@EnableScheduling` 존재 → 스윕은 실제로 돈다.** `MoyeotaApplication.java:8`. (스케줄러를 WS 와 공유하는 문제는 H-4.)

14. **`application-local.yaml` 은 git 미추적** (`git ls-files` 에 없음, `.gitignore` 처리). **저장소에 시크릿이 커밋돼 있지는 않다.** (배포 아티팩트 경로 리스크는 L-9.)

15. **`ChatRoomMatchingListener` 의 예외 처리 설계는 옳다.** `CHAT_ROOM_NOT_REQUIRED`(정원 2 미만)를 정상 흐름으로 조용히 return 하고 나머지는 로그만 남긴다 — 매칭 시작이 채팅방 생성 실패로 롤백되지 않는다.

16. **`ChatRoomAutoCreateIntegrationTest`** (`src/test/.../chat/app/`) 가 바로 이 AFTER_COMMIT 커밋 유실을 잡는 회귀 테스트로 존재한다. 같은 성격의 테스트가 `DispatchListener` 쪽엔 없다(H-1/M-1 참고).

---

## 부록 A — H-8 대조용: 엔티티 ↔ 테이블 목록

| 엔티티 | 테이블 | 주의할 컬럼 |
|--------|--------|-------------|
| `UserEntity` | `users` | `public_id` uuid NOT NULL UNIQUE, `nickname` UNIQUE(nullable), `login_type` varchar, `badge_id` bigint, `fcm_token`, `created_at` NOT NULL, `updated_at` |
| `UserProfileEntity` | `user_profile` | PK=`id`(users FK), `phone_number` UNIQUE NOT NULL, `birth_date` timestamptz, `gender` varchar, `pass_ci` |
| `LocalUserEntity` | `local_user` | PK=`id`, `login_id` UNIQUE NOT NULL, `password` NOT NULL |
| `RefreshTokenEntity` | `refresh_token` | **PK=`jti` uuid**, `user_id`, `expires_at`, `cancelled_at`, `created_at` NOT NULL |
| `PartyEntity` | `match_room` | `estimated_fare`/`estimated_time` NOT NULL, `route` TEXT NOT NULL, `taxi_driver_id`, **`match_started_at`**, `status` varchar |
| `PartyMemberEntity` | `user_match_room` | 복합 PK (`match_id`, `user_id`), `joined_at` NOT NULL, **created/updated 없음** |
| `DriverEntity` | `taxi_driver` | `user_id` UNIQUE NOT NULL, `qualification_number`, `bank_name`/`bank_number` NOT NULL, `status` varchar, `fcm_token` |
| `VehicleEntity` | `taxi` | `taxi_driver_id` FK NOT NULL, `type`/`seats`/`plate_number` NOT NULL |
| `DriverSettingEntity` | `driver_setting` | PK=`driver_id`(`@MapsId`), `call_enabled` NOT NULL |
| `ChatRoomEntity` | `chat_room` | `party_id` UNIQUE NOT NULL, `status` varchar |
| `ChatRoomUserEntity` | `chat_room_user` | 복합 PK (`user_id`, `chat_room_id`), `notification_muted` NOT NULL, `left_at` |
| `ChatMessageEntity` | `chat_message` | `content` varchar(1000), 인덱스 `idx_chat_message_room_id (chat_room_id, id)` |
| `ReportEntity` | `driver_report` | `reporter_id` NOT NULL, `status` varchar NOT NULL |
| `FavoritePlaceEntity` | `favorite_place` | — |

---

## 부록 B — 배포 환경변수 체크리스트

| 변수 | 미설정 시 | 심각도 |
|------|-----------|--------|
| `DATABASE_HOST` / `_USERNAME` / `_PASSWORD` | 기동 실패 | — |
| `JWT_SECRET` | 기동 실패 | — |
| `KAKAO_API_KEY` | **기동 실패** | fail-fast |
| `WS_ALLOWED_ORIGINS` | **기동 실패** | fail-fast |
| `NAVER_API_KEY_ID` / `NAVER_API_KEY` | 기동 성공, **방 생성·경로가 전부 502** | **M-7 위험** |
| `FCM_SERVICE_ACCOUNT_PATH` | 기동 성공, 푸시가 로그로만 나감(콜/도착 알림 미수신) | 조용한 실패 |
| Redis (`localhost:6379` 고정) | 기동 성공, 배차·채팅·위치가 런타임에 실패 | **M-8 위험** |
