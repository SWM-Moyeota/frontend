# 108 api-integrator — 묶음 D (백엔드 미지원 API 제거)

작성: 2026-10-08. 입력: `_workspace/107_input_backend_gap_removal.md`. 기준 백엔드: `../backend` develop 1a75f82 (컨트롤러 소스로 확인, 실서버 미기동).

## 백엔드 확인 결과
- `matching/rooms/{partyId}/location(s)`: 백엔드 develop 의 matching 패키지에 없음 → 제거 대상 확정.
- `ChatRoomController`(`/api/v1/chat-rooms`): `GET /{chatRoomId}` 하나뿐. `POST /chat-rooms`, `DELETE /chat-rooms/{id}` 없음 → 제거 대상 확정.
- 백엔드의 채팅방 위치 공유 경로(`ChatLocationController` `/api/v1/chat-rooms/{chatRoomId}/location*`, STOMP `/chat-rooms/{id}/location`·`/location/sync`)는 **프론트에서 쓰는 곳이 없다**(data/ grep 0건). 그러므로 지울 것도, 남길 것도 없다.

## D1 — 동승자 위치 공유 제거
- `MatchingApi`: `reportMyLocation`(POST `api/v1/matching/rooms/{partyId}/location`), `getMemberLocations`(GET `.../locations`) 삭제
- `PartyDtos.kt`: `MemberLocationRequestDto`, `MemberLocationResponse` 삭제
- `PartyMappers.kt`: `MemberLocationResponse.toMemberLocation()` 삭제
- `RideRepository`: `reportMyLocation`, `getMemberLocations` 삭제 (덕분에 엉뚱한 메서드에 붙어 있던 `previewRoute` KDoc 이 제자리로 돌아옴. MatchingApi 의 `getAssignedDriver` KDoc 도 마찬가지)
- `RemoteRideRepository`·`DummyRideRepository`: 구현 삭제
- `domain/model/Ride.kt`: `MemberLocation` 삭제 — presentation 사용처는 RideOngoingRoute(10,60-61,121,124)·RideOngoingScreen(55,119,361) 뿐, 모두 묶음 Q 의 삭제 범위
- 테스트: RemoteRideRepositoryTest·RemoteActivePartyRepositoryTest 의 fake override 2줄씩 + import 삭제(위치 기능 전용 테스트 케이스는 원래 없었음)

## D2 — 채팅방 생성/종료 죽은 코드 제거
- `ChatRepository`: `createChatRoom`, `closeChatRoom` 삭제
- `ChatApi`: `createRoom`(POST `api/v1/chat-rooms`), `deleteRoom`(DELETE `api/v1/chat-rooms/{chatRoomId}`) 삭제
- `ChatDtos.kt`: `CreateChatRoomRequestDto` 삭제, `ChatRoomResponse` 주석의 POST 언급 삭제
- `RemoteChatRepository`: 구현 삭제
- 테스트: RemoteChatRepositoryTest 의 FakeChatApi `createRoom/deleteRoom`, RemoteActivePartyRepositoryTest 의 FakeChatRepository override 삭제
- presentation/app 호출처: 0건

## 남겨둔 것
- `DispatchApi.getDriverLocation` / `DriverLocation`: 백엔드 `RideController` 에 실재하는 기사 위치 API — 무관
- `ChatMessageType.LOCATION`: 서버 메시지 타입 enum 매핑 — 무관
- `RemoteRideRepository.currentLocation`: 긴급 신고 좌표 공급자 — 무관
- `ChatApi` 의 `@POST`/`@DELETE`/`@Body` import: 다른 메서드(join/leave/mute/메시지)가 계속 사용

## 변경 시그니처 (compose-builder 공유)
- 삭제: `RideRepository.reportMyLocation(partyId, latitude, longitude)`, `RideRepository.getMemberLocations(partyId): List<MemberLocation>`, `ChatRepository.createChatRoom(...)`, `ChatRepository.closeChatRoom(chatRoomId)`, 모델 `MemberLocation`
- 추가: 없음

## 검증
`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :domain:compileDebugKotlin :data:testDebugUnitTest --console=plain -q` → 성공. 222 tests, 0 failures/errors/skipped.
:presentation/:app 은 빌드하지 않음 — Q 가 RideOngoingRoute/Screen 에서 MemberLocation·위치 호출을 지워야 컴파일된다.
