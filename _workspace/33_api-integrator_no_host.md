# 33 · api-integrator — 방장(host) 개념 제거 (data/ · domain/)

배경: 백엔드가 자동 기사 매칭으로 전환하며 매칭 도메인에서 방장 개념을 없앴다.
프론트에 남아 있던 "방장 추정" 로직과 하위호환 필드를 전부 걷어낸다.

## 백엔드 스펙 재확인 (소스 기준, 실서버 curl 미실행)

`../backend/src/main/java/team/codingforest/moyeota/matching/` 직접 확인:

| 확인 대상 | 결과 |
|---|---|
| `application/dto/PartyDetailResult` 응답 | host/hostId/isHost 필드 **없음**. `members[]` 는 `memberId` + `joinedAt` 뿐 |
| `application/dto/OpenPartyRequest.java` | `creatorId` 필드가 남아 있으나 주석 그대로 "creatorId는 토큰에서 온다. 본문의 creatorId 필드는 구버전 호환용으로 받기만 하고 무시한다" |
| `application/PartyApplicationService` | `Party.open(command.creatorId(), …)` — command 의 creatorId 는 `@CurrentUser` 토큰 주체에서 채워진다 |
| `domain/Party.java:75` | 생성자를 `members` 의 첫 멤버로 넣을 뿐, 별도 방장 표식 없음 |

→ 서버는 방장을 기록하지도, 응답하지도 않는다. 프론트의 "joinedAt 최소 = 방장" 은 서버 근거가 없는 추정이었다.

**엔드포인트 변경 없음** — 이번 작업은 순수 도메인 정리다. 연동한 경로·요청/응답 shape는 이전 리포트(01)와 동일.
실서버(localhost:8080)는 건드리지 않았다 → **실서버 미검증**(소스 기준 확정).

## 변경 내역

### domain/

| 파일 | 변경 |
|---|---|
| `domain/model/Ride.kt` | `hostId: String?` 필드 **삭제**. 관련 주석을 "서버에 방장 개념 없음"으로 교체 |
| `domain/model/NewParty.kt` | `hostId: Long` 필드 **삭제**(서버가 무시하던 구버전 호환 필드). KDoc에 "생성자는 토큰 주체" 명시 |
| `domain/repository/RideRepository.kt` | `createParty` KDoc에서 "여기만 아직 토큰 기준이 아니다 / 서버가 본문 creatorId로 방장을 정한다" 문단 제거 → 실제 동작(토큰 주체, 생성 응답에 멤버 목록 없음 → 상세 재조회)으로 교체 |
| `domain/session/UserSession.kt` | `currentUserId` KDoc의 "RideDetail 방장 배지 판정" 서술 제거. 매칭 계열은 전부 토큰 기준임을 명시하고, 화면의 "이 멤버가 나인가" 비교 제약만 남김 |

### data/

| 파일 | 변경 |
|---|---|
| `data/remote/PartyMappers.kt` | `List<MemberInfo>.inferHost()` **삭제**. 상세 매핑에서 방장 닉네임 분기 제거 → 전 멤버 `"멤버 {memberId}"`. `OpenPartyResponse.toRide(creatorId)` → **`toRide()`** (파라미터 제거), 멤버는 `currentMembers` 수만큼 자리 표시용으로 생성. 목록/생성 공통 `placeholderMembers(count)` 헬퍼 추출 |
| `data/remote/dto/PartyDtos.kt` | 주석만 정리 — "앱은 joinedAt 최소 멤버를 생성자로 추정" 서술 삭제, `OpenPartyRequestDto` KDoc 문구를 "생성자 id" 기준으로 교정. **DTO 필드는 무변경**(서버 shape 그대로) |
| `data/repository/RemoteRideRepository.kt` | `createParty` 가 `toRide(creatorId = request.hostId)` → `toRide()` |
| `data/repository/DummyRideRepository.kt` | `createParty` 반환 `Ride` 에서 `hostId` 대입 제거 |

### 매퍼 동작 변화 (qa-verifier 참고)

- 방 상세 화면 멤버 닉네임: 첫 멤버가 `"방장"` 으로 표시되던 것이 → `"멤버 {memberId}"` 로 통일.
- 방 생성 직후 멤버: 이전엔 `hostId` 기반 멤버 1명(`"방장"`), 이제 `currentMembers` 개수만큼 `"멤버 N"`.
  서버 응답에 멤버 식별자가 없는 건 이전과 동일하며, 실제 id 는 `getPartyDetail` 로만 얻는다.

## Repository 시그니처 변화 (compose-builder 통지 대상)

인터페이스 메서드 시그니처 자체는 **동일**하다. 바뀐 건 인자로 넘기는 도메인 모델의 필드다.

```kotlin
// 변경 없음
suspend fun createParty(request: NewParty): Ride

// NewParty 에서 hostId 가 사라졌다 — 호출부는 그 인자를 지우기만 하면 된다
NewParty(departureLat, departureLng, destinationLat, destinationLng,
         departure, destination, capacity, departureRadius, destinationRadius)

// Ride 에서 hostId 가 사라졌다 — "내가 방장인가" 를 도메인에서 알 방법은 없다
```

## 깨지는 presentation 참조 지점 (수정은 compose-builder 몫)

`./gradlew :presentation:compileDebugKotlin` 실측 에러 **2건**:

| # | 파일:라인 | 현재 코드 | 컴파일러 메시지 | 권장 수정 |
|---|---|---|---|---|
| P-1 | `presentation/.../feature/home/DestinationConfirmRoute.kt:62` | `hostId = userSession.currentUserId,` (NewParty 생성 인자) | `No parameter with name 'hostId' found.` | 해당 인자 줄 **삭제**. `userSession` 이 이 파일에서 다른 데 안 쓰이면 ViewModel 의존성에서도 정리 |
| P-2 | `presentation/.../feature/matching/RideDetailRoute.kt:123` | `isHost = current.ride.hostId == currentUserId,` | `Unresolved reference 'hostId'.` | `isHost` 판정 자체를 제거. 아래 P-3 과 함께 처리해야 함 |

### 컴파일은 통과하지만 남아 있는 방장 잔재 (같이 정리 권장)

| # | 파일:라인 | 내용 | 권장 수정 |
|---|---|---|---|
| P-3 | `RideDetailScreen.kt:99` | `isHost: Boolean = true` 파라미터 | 파라미터 제거 |
| P-4 | `RideDetailScreen.kt:356~375` | `if (isHost)` 로 「이 인원으로 출발」 CTA 노출/미노출 분기, else 브랜치 문구 `"방장이 출발을 결정하면 시작돼요"` | 방장이 없으므로 분기 삭제 — **CTA를 전원에게 노출**할지 자동 매칭 전환에 맞춰 CTA 자체를 없앨지는 제품 판단 필요. else 문구는 어느 쪽이든 삭제 |
| P-5 | `RideDetailScreen.kt:93` | KDoc `"「이 인원으로 출발」 → … 방장에게만 노출"` | P-4 결정에 맞춰 갱신 |
| P-6 | `JoinConfirmScreen.kt:65,221~226,442~470` | `JoinHost` 더미 / `val host = ride.members.firstOrNull()` / `HostRow(host: User …)` — 첫 멤버를 방장처럼 부르는 명명 | 의미상 "대표로 보여 주는 동승자 1명" 이므로 `host` → `representative`/`firstMember` 로 **리네이밍**(동작 변화 없음) |
| P-7 | `RideDetailRoute.kt:104~107` | 주석 "방 상세 응답에도 '내 멤버십/방장 여부'가 없다" (D-5 한계) | 방장 부분 삭제, "내 멤버십 여부" 제약만 남김 |

## 테스트

`data/src/test/kotlin/com/moyeota/data/remote/PartyMappersTest.kt`
- 삭제: `joinedAt 이 없는 멤버는 방장 추정에서 뒤로 밀린다`
- 교체: `백엔드에 host 필드가 없어 가장 먼저 참여한 멤버를 방장으로 추정한다`
  → **`멤버는 전부 동등하게 매핑된다 — 방장 추정이 없다`** (서버 순서 유지 + 닉네임 균일 검증, 추정 로직 부활 방지 회귀 테스트)
- 갱신: 생성 응답 매핑 테스트 2건(`toRide()` 무인자), 상세 역직렬화 테스트(hostId 단언 → `members.map { it.id }` 단언)
- 유지: `생성 요청 본문에 사용자 id 를 싣지 않는다` — 인코딩 결과에 `creatorId`/`hostId` 문자열이 없음을 계속 못박는다

`data/src/test/kotlin/com/moyeota/data/repository/RemoteRideRepositoryTest.kt`
- 교체: `방 생성은 hostId 를 서버로 보내지 않고 로컬 표시용 방장에만 쓴다`
  → **`방 생성 요청 본문에는 좌표 라벨 정원만 실린다 — 생성자는 토큰이 정한다`**

### 결과

```
./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin --console=plain
BUILD SUCCESSFUL in 5s
```

`./gradlew :presentation:compileDebugKotlin` 은 위 P-1·P-2 로 **실패 (예상된 실패)**. compose-builder 수정 후 `:app:assembleDebug` 재확인 필요.

## 리더 확인 요망

- **P-4 제품 판단**: 「이 인원으로 출발」 CTA를 (a) 전원에게 노출, (b) 자동 매칭이므로 화면에서 제거 — 어느 쪽인지. data/ 쪽 결정 사항은 없고 화면 동작 정의가 필요하다.
- **실서버 미검증**: 스펙은 백엔드 소스 기준으로 확정했고 localhost:8080 은 건드리지 않았다.
