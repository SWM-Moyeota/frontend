# 68 · 동승자 실시간 위치 (26 운행 중 지도)

작성 2026-09-13.

## 요청
"실시간 위치를 공유해도 상대방의 위치가 실시간으로 안뜨더라고"

## 원인
**서버에 승객 위치 API 가 아예 없었다.** 위치 관련 엔드포인트를 전수 확인한 결과 기사용 셋뿐이다
(`POST /dispatch/online`, `POST /dispatch/location`, `GET /dispatch/rides/{id}` = 승객이 **기사** 위치를 읽는 것).
그런데 앱에는 「실시간 위치 공유 중 · 동승자에게 내 위치가 보여요」 배너가 이미 있어, 아무 일도 일어나지 않으면서
사용자에게는 공유되고 있다고 말하고 있었다.

## 서버 (백엔드 PR #100)
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/matching/rooms/{partyId}/location` | 내 위치 보고 → 204 |
| GET | `/api/v1/matching/rooms/{partyId}/locations` | **나 제외** 동승자 위치 `[{publicId, nickname, latitude, longitude}]` |

Redis 문자열 키 `party:{partyId}:member:{memberId}` + **TTL 60초**. 방이 끝나면 남지 않고 앱을 끈 사람의
낡은 점도 사라진다. 보고·조회 모두 멤버 확인(403 NOT_PARTY_MEMBER / 404 PARTY_NOT_FOUND).

## 앱
- domain `MemberLocation(publicId, nickname, latitude, longitude)` + `RideRepository.reportMyLocation` / `getMemberLocations`
- data: DTO·API·매퍼·`RemoteRideRepository` 구현 (Dummy·테스트 페이크 포함)
- `RideOngoingViewModel.shareLocations(myPosition)` — **5초 주기**로 내 위치 보고 + 동승자 조회.
  완료 폴링(4초)과 따로 도는 루프다(주기가 다르고 한쪽 실패가 다른 쪽을 멈추면 안 된다).
  화면을 떠나면 멈추고, 60초 뒤 서버 TTL 이 좌표를 지우므로 별도 「공유 종료」 호출이 필요 없다.
  최신 좌표는 `rememberUpdatedState` 로 읽는다 — key 로 넣으면 1초마다 루프가 재시작돼 주기가 무너진다.
- `RideOngoingScreen`: 동승자마다 **초록 마커 + 닉네임 캡션**(`MarkerPickup`). 파랑은 내 위치·출발,
  빨강은 도착이라 겹치면 구분이 안 된다. 좌표는 마커마다 `latLngOrNull` 검증.
- designsystem `MapMarker` 를 public 으로 (RouteMapView 바깥에서도 쓰도록)

## 검증
로컬 백엔드(dev 프로필 + Postgres V1~V4 + Redis)에 기사까지 시드해 파티를 IN_RIDE 로 만든 뒤,
에뮬레이터 앱을 그 파티의 승객으로 로그인시켜 확인:
- 서버에서 동승자 토큰으로 조회하니 **앱이 5초마다 내 위치를 보고**하고 있었다(에뮬 GPS 좌표 그대로)
- 동승자 좌표를 curl 로 보고하니 **26 지도에 초록 마커 + 닉네임(「동승자나」)이 표시**됨
- 좌표를 바꿔 보고하면 마커도 따라 이동

## 남은 것
- 21 대기 · 25 배차에서도 같은 공유를 켜면 **탑승 전에 서로 찾기**가 된다(지금은 26 운행 중만). 서버 API 는 그대로 쓰면 된다
- 채팅 배너의 토글은 아직 장식이다 — 끄면 보고를 멈추도록 연결할 수 있다
