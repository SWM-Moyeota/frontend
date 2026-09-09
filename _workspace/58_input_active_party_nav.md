# 58 · 입력 — 진행 중 방 복귀·채팅↔매칭 화면 내비게이션

사용자 원문: "매칭방 합류 → 정원 충족 → 매칭 → 채팅방 생성돼 채팅 가능하게 해놨는데, 뒤로가기로 채팅방에 접근하면 다시 매칭 진행 화면으로 돌아갈 수 없다."

## 원인
- 25/26/21 의 「채팅 열기」가 채팅 **탭**으로 이동(navigateTab) → 매칭 화면이 스택에서 빠짐.
- 진행 중 방 id 가 화면 로컬 상태뿐 → 앱 재시작·탭 이동 후 복귀 근거 없음. 합승 탭 「진행 중 탑승 · 보기」 배너는 목업 34(내 탑승)로 가고 34 도 하드코딩.
- 백엔드 `GET /matching/rooms/me` 부재 — 앱 단독 해법 필요.

## 도메인 계약 (리더 확정)
```kotlin
// domain/repository/ActivePartyRepository.kt (신규)
interface ActivePartyRepository {
    suspend fun remember(partyId: String)          // 방 생성·합류 성공 직후 (DataStore 영속)
    suspend fun clear()                             // 나가기 성공·FINISHED/CANCELED 확인 시
    /** 로컬 기억 → 상세 조회로 검증(내가 멤버 && status ∈ {RECRUITING, DISPATCHING, ONGOING}) → 없으면
     *  GET /chat-rooms/me 의 partyId 들을 순회해 같은 조건으로 탐색. 못 찾으면 null + 로컬 기억 삭제. */
    suspend fun resolve(): Ride?
}
// 진행 단계 판정은 기존 Ride.status/driverId 를 그대로 사용(55 리포트 규칙). data 구현: RemoteActivePartyRepository(rideRepository, chatRepository, dataStore, session) — 계정 전환 시 uuid 가드로 기억 무효.
```

## 화면 계약
- `MainNavGraph.navigateToStage(ride: Ride)`: RECRUITING→21, DISPATCHING(driverId null)→25b, DISPATCHING(driverId 있음)→25c/25, ONGOING→26, 그 외→홈. 스택은 홈 위에 단계 화면 1개(resetTo 대신 popUpTo HOME).
- 「채팅 열기」(21/25/26): 채팅방을 **독립 목적지**(`Routes.chatRoom(roomId)`)로 push → 뒤로가기 = 원래 화면. 채팅 탭의 목록에서 여는 경로는 기존 유지.
- 채팅방 헤더: 방의 partyId 로 진행 중이면 「매칭 화면으로 →」 버튼 → navigateToStage.
- 합승 탭·홈 배너 「진행 중 탑승 · 보기」: `resolve()` 결과가 있을 때만 표시, 탭 시 navigateToStage. 화면 복귀(onResume)마다 재조회(가벼움: 로컬 기억이 있으면 상세 1회).
- 34 내 탑승: 목업 데이터 제거, `resolve()` 결과(출발·도착·상태·동승자)로 채우고 「진행 상황 보기」→ navigateToStage. 없으면 빈 상태 문구.
- 앱 시작 시(로그인 상태) `resolve()` 가 진행 중 방을 찾으면 홈 대신 해당 단계로 바로 진입(사용자 기대: 앱 껐다 켜도 매칭 화면 복귀).
