# 실시간 매칭 연동 — 실기 검증 (2026-10-24)

`02_realtime-matching.md` 가 계획으로 남긴 연동을 구현하고 실기로 확인한 기록.
계획에서 **보류**로 두었던 화면 노출까지 했다 — 사용자가 OSRM 이 운영에 떠 있다고 확인해 줬기 때문이다.

## 환경 — H2 로는 이 기능을 못 본다

이 엔드포인트는 PostGIS 함수(`ST_DWithin`)를 쓰는 네이티브 쿼리라 **기본 H2 프로파일에서는 500 이
난다**(`Function "st_dwithin" not found`). 그래서 평소 QA 환경으로는 한 줄도 확인할 수 없다.

```bash
docker run -d --name moyeota-qa-pg -p 5432:5432 \
  -e POSTGRES_DB=moyeota -e POSTGRES_USER=moyeota -e POSTGRES_PASSWORD=moyeota \
  postgis/postgis:16-3.4

cd ../backend && SPRING_PROFILES_ACTIVE=dev TAXI_ENABLED=false \
  DATABASE_HOST=localhost DATABASE_NAME=moyeota \
  DATABASE_USERNAME=moyeota DATABASE_PASSWORD=moyeota ./gradlew bootRun
```

Flyway 가 V1~V20 을 올리고 V20 이 `CREATE EXTENSION postgis` 를 한다 — 적용 확인:

```
flyway_schema_history  20건, 최신 version 20
pg_extension           postgis
```

**dev 프로파일에서는 인증번호가 고정값(`000000`)이다**(`application-dev.yaml` 의 `sms.fixed-code`).
로그를 긁는 `signup.sh` 대신 `signup_dev.sh` 를 쓴다.

### OSRM 은 가짜로 세웠다

진짜 이미지는 `south-korea-latest.osm.pbf`(약 1GB)를 받아 전처리해야 떠서(`infra/osrm/Dockerfile`)
QA 한 번에 쓰기엔 과하다. 백엔드가 쓰는 건 `/table/v1/foot/...` 의 `durations` 행렬 하나뿐이라
(`OsrmWalkingClient`, `OsrmTableResponse`) 그 모양만 맞춘 파이썬 서버를 5000 포트에 띄웠다
(`scratchpad/fake_osrm.py`, 하버사인 거리 ÷ 1.3 m/s).

**`urlparse` 를 쓰면 안 된다** — OSRM 은 좌표 구분자로 `;` 를 쓰는데 `urlparse` 가 그걸 path
params 로 잘라낸다. `self.path.partition("?")` 로 직접 쪼개야 한다.

## 결과 — 세 경로 모두 통과

### ① 후보가 있을 때 (200 + 배열)

방 2개를 찾는 사람의 출발·도착 양쪽 300m 안에 만들고(주인은 남겨 둬야 방이 안 닫힌다) 호출:

```json
[{"summary":{"id":3,...,"departure":"강남역 1번 출구","destination":"역삼역","capacity":3},"totalSeconds":174},
 {"summary":{"id":4,...,"departure":"강남역 2번 출구","destination":"역삼역","capacity":3},"totalSeconds":350}]
```

16 모달 화면:

```
걸어서 가까운 방
타러 가고, 내려서 걷는 시간을 더한 값이에요

강남역 1번 출구 → 역삼역    최대 3명    걸어서 3분
강남역 2번 출구 → 역삼역    최대 3명    걸어서 6분
```

- 174초 → **3분**, 350초 → **6분**(올림). 서버 정렬 순서 그대로, 앱이 재정렬하지 않는다.
- 카드가 2장이면 접힌 시트에서 둘째가 하단에 가려지는데 **스크롤하면 온전히 보인다** — 하단 요금·CTA
  는 겹치는 레이어가 아니라 형제 영역이다(스크린샷 2장으로 확인).

### ② 탭 → 19 합류 확인

추천 카드를 누르면 **방을 만들지 않고** `JOIN_CONFIRM` 으로 간다. 거기서 방 상세를 읽어
**추천 응답에 없던 값이 채워진다** — 이게 설계의 핵심이다:

```
GET /api/v1/matching/rooms/3  →  200
화면: 「1명 참여 · 목표 3명 · 내가 2번째」, 「2,300원」, 방장 「방주인A」
```

검증 당시 추천 카드에서는 인원·요금을 못 썼다(응답에 `currentMembers`·요금이 없었다). 지어내지
않고 비워 둔 값이 합류 화면에서 사실로 채워진다 — **인원은 그 뒤 Backend #260 으로 해결됐다**
(아래 「후속」). 요금은 여전히 합류 화면에서만 나온다.

### ③ OSRM 장애 (502)

가짜 OSRM 을 내리고 다시 들어갔다.

```
<-- 502 http://10.0.2.2:8080/api/v1/matching/rooms/realtime
{"code":"ROUTE_SEARCH_FAILED","message":"경로 조회에 실패했습니다..."}
```

16 모달에서 **「걸어서 가까운 방」 섹션만 사라지고** 경로 미리보기·요금·CTA 는 그대로다.
**에러 배너를 띄우지 않는다** — 방을 만들러 온 사용자에게 추천 실패를 자기 잘못처럼 보이게 하면
안 되기 때문이다. 빈 결과(200 `[]`)와 화면이 같다.

## 계약 정정 — 리포트 02 의 「함정 2」는 틀렸다

02 는 "요청 필드를 빠뜨리면 Jackson 이 `0.0` 을 넣고 (0,0) 이 서비스 지역 밖이라
**400 `OUT_OF_SERVICE_AREA`** 가 떨어진다" 고 적었다. 실측은 다르다:

```
destLat/destLng 대신 destinationLat/destinationLng 로 보냄
→ 400 {"code":"INVALID_REQUEST","message":"요청 본문이 없거나 형식이 잘못되었습니다."}
   (서버 로그: MismatchedInputException)
```

record 바인딩이 primitive 누락을 **0.0 으로 메우지 않고 역직렬화 자체를 거부**한다. 결론(필드명을
틀리면 400) 은 같지만 **코드가 다르다** — `isOutOfServiceArea` 로 분기하면 이 경우를 못 잡는다.
어느 쪽이든 사용자에게는 "서비스 지역이 아니에요" 로 보이면 안 되므로, 화면은 지금처럼 사유를
가리지 않고 **섹션을 숨기는** 쪽이 맞다.

## 후속 — `currentMembers` 가 들어왔다 (2026-10-24)

백엔드 **#260** 이 `NearbyPartySummary` 에 `Integer currentMembers` 를 추가했고(`#261` 로 main 까지),
앱이 그걸 받아 카드에 「2/3명」을 쓴다. 17 탐색 카드와 같은 문구·같은 경로(`placeholderMembers`)다.

**`Int? = null` 로 받는다 — `Int = 0` 이 아니다.** 후보는 `status = 'ACTIVE'` 인 방만 오고 그런 방에는
최소 방장 1명이 있으므로 **0 은 나올 수 없는 값**이다. 0 으로 접으면 옛 배포를 볼 때 화면이
「0/3명」이라는 없는 상태를 그린다. 모를 때는 정원만 쓴다(`headcountLabel`).

`Ride.members` 는 **머릿수만 맞춘 자리표시자**다(누가 들었는지는 서버가 안 준다). 개수는 믿어도
되지만 닉네임·프로필을 그리면 가짜 평판이 보인다 — `MatchCandidateMapperTest` 가 그 전제를 못 박아 둔다.

### 추천 카드와 합류 화면의 숫자가 같은가 — 확인했다

같다. **두 숫자가 같은 행을 센다**(backend `main` 기준):

| | 계산식 |
|---|---|
| 추천 카드 (`NearbyPartySummary`) | `COUNT(*) FROM user_match_room WHERE match_id = m.id` |
| 합류 화면 (`PartyDetailResult.from`) | `party.getMembers().size()` |

`PartyMemberEntity` 는 `@Table("user_match_room")` 이고 **소프트 삭제 컬럼이 없다.** `Party.leave` 가
`members.remove(...)` 를 하고 `PartyEntity.members` 가 `orphanRemoval = true` + `syncMembers` 의
`removeIf` 라 **나가면 행이 지워진다.** 그래서 상태 필터 없는 `COUNT(*)` 와 `members.size()` 가
항상 일치한다 — 카드가 「2/3명」인 방에 들어가면 합류 화면도 「2명 참여」다.

**탈퇴 사용자는 양쪽 모두 자리를 차지한 것으로 센다.** 탈퇴는 사용자 쪽 소프트 삭제라서
`user_match_room` 행은 남고 닉네임만 서버가 가려 보낸다. 좌석이 실제로 묶여 있으니 세는 게 맞고,
무엇보다 **두 화면이 같은 수를 보여 준다**.

## 남은 것

- **운영 OSRM 미검증.** 사용자가 "prod·dev 가 한 서버를 공유한다" 고 확인해 줬지만 앱으로 실측하지는
  않았다. 운영에서 첫 후보가 잡히는 순간이 그 검증이다 — 그 전까지는 후보 0건이라 502 가 숨는다.
- **prod·dev 가 OSRM 한 대를 공유한다.** dev 에서 과부하를 주면 운영 추천이 같이 멈춘다.
  비공개 테스트 중이라 알고는 있어야 한다.
