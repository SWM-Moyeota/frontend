# 71 · 채팅 실시간 수신 (STOMP over WebSocket)

작성 2026-09-15.

## 먼저 바로잡은 것
"CloudFront 가 WebSocket 을 막고 있다"고 보고했던 건 **오진**이었다. curl 이 CloudFront 와 HTTP/2 로 협상했는데
HTTP/2 에서는 `Connection: Upgrade` 헤더 자체가 금지라 거부된 것이다. `--http1.1` 로 다시 보내니 **101** 이 온다.

배포 설정도 이미 적합했다: 캐시 정책 `Managed-CachingDisabled`, 원본 요청 정책 `Managed-AllViewer`(Upgrade·Connection 전달), 메서드 7종 허용.

`WS_ALLOWED_ORIGINS=http://localhost:*` 도 **바꿀 필요 없다.** `Origin` 은 클라이언트(웹 페이지)의 출처라
안드로이드 앱은 아예 보내지 않는다. API 도메인을 넣으라던 것도 내 오류다(내가 curl 로 그 값을 직접 넣어 만든 가짜 조건이었다).

## 서버 계약
| 항목 | 값 |
|---|---|
| 엔드포인트 | `wss://{host}/ws-chat` |
| 인증 | STOMP **CONNECT 프레임**의 `Authorization: Bearer {access}` (HTTP 헤더 아님) |
| 구독 | `/sub/chat-rooms/{chatRoomId}` — 서버가 **활성 참여자**인지 검사 |
| 페이로드 | `ChatMessageResult` = REST 메시지 응답과 **같은 shape** |

## 앱 구현
- 의존성 `org.hildan.krossbow:krossbow-stomp-core` + `krossbow-websocket-okhttp` **9.3.0**
  (10.0.0 은 Kotlin 2.4 로 빌드돼 프로젝트 2.2.10 과 메타데이터 비호환)
- `ChatSocket` (data/remote/chat) — 연결·구독·재연결(3초). `subscribeText` + 수동 파싱이라 변환 계층 불필요.
  프레임 본문이 REST 와 같아 `ChatMessageResponse` 를 그대로 재사용
- `NetworkModule` 에 **소켓 전용 OkHttp** 추가 — 인증 인터셉터 미부착(STOMP 인증은 프레임 헤더),
  `readTimeout=0`(상시 연결), `pingInterval=20s`
- `ChatRepository.observeMessages(roomId): Flow<ChatMessage>` — 소켓이 밀어 준 프레임도 조회와 **같은 경로로
  신원 해석**(모르는 발신자면 참여자 사전 1회 갱신)
- `ChatRoomViewModel` — 화면이 보이는 동안 구독, 떠나면 해제. **폴링은 남긴다**: 소켓이 살아 있으면 주기를
  3초 → 20초로 늦추고, 끊기면 다시 3초. 재연결 사이에 오간 메시지는 소켓으로 오지 않으므로 커서 조회가 메운다

## 검증 (에뮬레이터 + 배포 서버)
- 핸드셰이크: `<-- 101 https://api.moyeota.p-e.kr/ws-chat`, `Sec-WebSocket-Protocol: v12.stomp`, `ChatSocket: 연결됨 roomId=26`
- 상대가 보낸 메시지 **3건 연속** 모두 **1초 안에** 화면 반영. 같은 구간의 REST 폴링은 1건뿐(주기가 20초로 늦춰짐)
- 폴링만 쓰던 때는 최대 3초 + 응답 1.1초였다

## 남은 것
- 동승자 위치는 팀원분 `feature/real-time-location-share` 브랜치가 머지되면 **같은 소켓**에 얹는다
  (스냅샷이 STOMP `/pub/chat-rooms/{id}/location/sync` 전용)
- 메시지 **전송**은 아직 REST 다. STOMP `/pub/chat-rooms/{id}/messages` 로 옮길 수 있지만, 전송은 응답으로
  결과를 받아야 해서(낙관적 UI 없음) 지금 구조에선 REST 가 단순하다
- 읽음 처리도 REST 유지(서버가 WS 경로를 추가했지만 REST 도 그대로 있다)
