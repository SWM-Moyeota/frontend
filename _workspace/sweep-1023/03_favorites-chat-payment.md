# 백엔드 스윕 03 — 즐겨찾기 · 채팅 · 결제

- **조사 범위**: 백엔드 `origin/develop`(= `82f000f`), 기준점 `284a950` 이후
- **백엔드 저장소**: `/Users/sungyoon/Desktop/moyeota/backend` — **읽기만 함. 서버 기동/종료 안 했고 수정하지 않았다.**
  (조사 시점 워킹트리에 팀원 작업 `M src/main/java/.../driver/domain/Driver.java` 가 있었으나 손대지 않았다.)
- **근거 수준**:
  - 즐겨찾기 **계약(경로·메서드·JSON 키·타입·에러코드)은 운영 OpenAPI 실측**이다 — `https://api.moyeota.co.kr/v3/api-docs` → **HTTP 200, 55,735 bytes** (2026-10-23 조사 시점). Jackson 기본 동작 추측을 쓰지 않았다.
  - 채팅·결제·스케줄러는 소스 기준(실행 중 서버에서 관측 불가한 내부 동작).
  - **읽기 전용 호출만 했다**: `GET /v3/api-docs`, `GET /api/v1/users/me/favorite-places`(무토큰 401 확인). **prod 에 DELETE/POST 를 보내지 않았다.**
- **전제 (코디네이터 제공, 재확인 완료)**: `origin/main^{tree}` == `origin/develop^{tree}` == `065ebc24` → **이 변경들은 전부 이미 prod 에 떠 있다.** "운영에 없으니 맞추면 안 된다" 는 경우가 없다.
- SSE·실시간매칭은 다른 에이전트 담당이라 손대지 않았다. `b8a308e`(SSE heartbeat 제거), `426a527`/`95059cb`(실시간 매칭)는 이 리포트 범위 밖이다.
- **조사 범위 정정 반영**: PR #238 의 본체 커밋 `cdc39da`(10/15 삭제 API 최초 추가) · `3b1d6ed`(10/15 리뷰 반영 + 전체 삭제 + **이슈 #231 수정**) 포함. §1-3 식별자 3단 변경 이력과 §1-4 가 이 둘을 다룬다.

## 0. 한 줄 결론

| 영역 | 판정 | 요약 |
|------|------|------|
| 즐겨찾기 `placeName` → `place_name` | **무해** | **JSON 키는 안 바뀌었다.** JPA 컬럼 매핑만 명시화한 것 — `imageUrl→imageKey` 사고와는 **다른 종류**다 |
| 즐겨찾기 응답에 `placeId` 추가 | **무해** | `ignoreUnknownKeys=true` 가 흘린다. 단 앱이 그 값을 **버리고 있어** 삭제 기능을 만들 수 없다(기능 공백) |
| 즐겨찾기 삭제 API 3개 신설 (prod 실재) | **무해(미사용)** | 앱에 삭제 엔드포인트가 아예 없다. 식별자가 `placeName`→쿼리→`placeId` 로 두 번 바뀌었지만 **앱은 그 중 어느 버전도 연동한 적이 없어** 노출되지 않았다 |
| 즐겨찾기 **삭제 UI 유무** | **UI 자체가 없음 → 「화면부터 필요하다」** | 비활성·숨김 버튼이 아니다. 읽기 전용 카드만 있고 롱프레스·편집모드·X 버튼이 전무하다 → §1-7 |
| 즐겨찾기 등록 500 (이슈 #231 / D-4) | **조용히 틀려짐 (문서)** | **prod 에서 이미 고쳐졌다. 즉 기존 앱 빌드의 ★ 등록이 지금 갑자기 성공하기 시작한다.** 앱 KDoc 1곳이 "항상 500" 이라 거짓, QA 전제도 낡았다 |
| 채팅 나가기 서버측 구독 해제 | **무해** | 앱은 UNSUBSCRIBE 프레임을 **보내지 않는다**(세션째로 DISCONNECT). 이중 처리 없음. 서버가 죽이는 목적지도 앱이 쓰는 두 구독과 안 겹친다 |
| 결제(포트원) | **무해** | 백엔드 `origin/develop` 에 **결제 코드가 한 줄도 없다.** `application.yaml` 환경변수 키 2줄뿐. 앱도 결제 기능이 없다 |
| `@Scheduled` 스레드 풀 | **무해** | 요청/응답 계약 변화 0. 앱에는 타이밍 신뢰도 개선으로만 나타난다 |

**깨짐(crash/4xx)은 한 건도 없다. 운영 OpenAPI 와 현행 앱 DTO 를 필드 단위로 대조한 결과 불일치 0건이다.**

주의할 것 두 가지:
1. **"서버가 고쳐졌다는 사실을 앱 문서가 모른다"** — 즐겨찾기 등록이 prod 에서 이미 성공하기 시작했는데 KDoc 은 "항상 500" 이라고 적혀 있다. 코드는 안 고쳐도 되고, **문서 2곳과 QA 전제**를 고쳐야 한다(§1-4).
2. **삭제는 "깨진 기능" 이 아니라 "안 쓰는 신규 능력" 이다** — 화면부터 필요하다(§1-7).

---

## 1. 즐겨찾기 (PR #238 `feature/favorite-place-delete`)

### 1-0. 운영 계약 **실측** (prod OpenAPI, 2026-10-23)

`curl -sS https://api.moyeota.co.kr/v3/api-docs` → **200**. 즐겨찾기 경로 5개, 전부 존재:

```
GET    /api/v1/users/me/favorite-places              | 내 즐겨찾기 목록
POST   /api/v1/users/me/favorite-places              | 즐겨찾기 등록
DELETE /api/v1/users/me/favorite-places/{placeId}    | 즐겨찾기 삭제        ← 경로 파라미터 integer/int64
POST   /api/v1/users/me/favorite-places/batch-delete | 즐겨찾기 다건 삭제
DELETE /api/v1/users/me/favorite-places/all          | 즐겨찾기 전체 삭제
```

**스키마 — 실제 JSON 프로퍼티명 (추측 아님, 운영 스펙에서 그대로 추출):**

| 스키마 | 프로퍼티 (OpenAPI type/format) |
|---|---|
| `FavoritePlaceRequest` | `placeName` string · `roadName` string · `latitude` number/double · `longitude` number/double |
| `FavoritePlaceResponse` | **`placeId` integer/int64** · `placeName` string · `roadName` string · `latitude` number/double · `longitude` number/double · `placeSequence` integer/int32 |
| `FavoritePlaceListResponse` | `places` array → `FavoritePlaceResponse` |
| `FavoritePlaceDeleteRequest` | `placeIds` array&lt;integer&gt; |

`DELETE /{placeId}` 파라미터 실측: `{"name":"placeId","in":"path","schema":{"type":"integer","format":"int64"},"required":true}`

**에러 응답 실측:**

| 엔드포인트 | 문서화된 응답 |
|---|---|
| `POST` 등록 | 204 / 400 `INVALID_PLACE_NAME`·좌표누락 / 401 |
| `DELETE /{placeId}` | 204 / 400 `INVALID_PLACE_ID` / 401 / 404 `FAVORITE_PLACE_NOT_FOUND` |
| `POST /batch-delete` | 204 / 400 `INVALID_PLACE_ID`·`INVALID_PLACE_IDS` / 401 / 404 `FAVORITE_PLACE_NOT_FOUND` |
| `DELETE /all` | 204 / 401 / 404 `FAVORITE_PLACE_NOT_FOUND` |

무토큰 `GET` 실측 → `HTTP 401 {"code":"USER005","message":"로그인이 필요합니다."}`
→ 앱의 `ApiErrorDto(code, message)`(`data/.../dto/ErrorDtos.kt:13-16`)와 shape 일치 확인.

> 🔸 **문서 ↔ 구현 불일치 1건 (실측으로 드러남)**: 운영 OpenAPI 의 `POST` 등록은 **409 를 문서화하지 않는다**(204/400/401 만). 그러나 소스는 중복·10개초과를 **409 CONFLICT** 로 던진다 — `PlaceErrorCode.java:9-10` 의 `FAVORITE_PLACE_DUPLICATED`/`FAVORITE_PLACE_LIMIT_EXCEEDED` 가 `HttpStatus.CONFLICT`, 발생 지점은 `FavoritePlaceService.java:34-36`·`:38`. 컨트롤러 `@ApiResponses`(`FavoritePlaceController.java:30`)가 409 를 안 적어서 스펙에 안 나온 것이다.
> **실제 응답을 우선한다 → 중복·초과는 409.** 앱은 `catch (e: Exception)` 로 전부 같은 문구를 띄우므로(`DestinationRoute.kt:180-182`) 동작에는 영향이 없다. §1-4 의 주석 수정에 이 번호를 쓴다.
>
> 🔸 springdoc 기본값이라 모든 스키마에 `required`·`nullable` 표기가 **없다** → **OpenAPI 는 nullability 를 보증하지 않는다.** 널 가능성은 §1-2 처럼 서버 소스로 판단해야 한다.

### 1-1. 함정 검증 — `placeName` → `place_name` 은 **JSON 키 변경이 아니다** (실측 확정)

지시받은 최우선 확인 사항. 끝까지 추적한 결과 **엔티티 컬럼 매핑만 바뀌었고 API 요청/응답 필드명은 그대로**다.

**근거 1 — 변경된 애노테이션이 붙은 위치가 엔티티다 (DTO 가 아니다).**
`8e7a920` 의 diff 는 1줄, 대상 파일은 `FavoritePlaceEntity.java` 하나뿐이다:

```
backend/src/main/java/team/codingforest/moyeota/place/favorite/infrastructure/FavoritePlaceEntity.java:48-49
    @Column(name = "place_name")
    private String placeName;
```

`@Column` 은 **DB 컬럼명**을 정하는 JPA 애노테이션이고, 이 클래스는 `infrastructure` 패키지의 `@Entity` 다. HTTP 직렬화에 관여하지 않는다.

**근거 2 — 실제 API 응답 DTO 는 필드명이 `placeName` 그대로다.**
```
backend/src/main/java/team/codingforest/moyeota/place/favorite/dto/FavoritePlaceResponse.java:5
    public record FavoritePlaceResponse(Long placeId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence)
```
요청 DTO 도 동일 (`FavoritePlaceRequest.java:5-6`, 기준점 대비 **빈 줄 1개**만 추가된 게 전부 — `git diff 284a950 origin/develop -- .../FavoritePlaceRequest.java` 확인).

**근거 3 (결정적) — 운영 서버가 내보내는 스펙이 `placeName` 이다.**
prod OpenAPI 의 `FavoritePlaceResponse`·`FavoritePlaceRequest` 프로퍼티명 실측 결과: **`placeName`, `roadName`, `placeSequence`, `placeId`** — camelCase. `place_name` 은 스펙 어디에도 없다(§1-0 표).
추측이 아니라 **운영 중인 그 앱이 생성한 스펙**이다. (보조 근거: 백엔드에 `PropertyNamingStrategy`/`spring.jackson.property-naming-strategy`/`@JsonNaming`/`SNAKE_CASE` 설정이 0건 — `grep -rn -i "naming|SNAKE_CASE" src/main/resources/ src/main/java/`.)

**근거 4 — 백엔드가 직접 쓴 수동 확인 시나리오가 camelCase 를 읽는다.**
```
backend/http/favorite-place.http:124-127
@minsuHome   = {{listMinsu.response.body.places[0].placeId}}
@minsuOffice = {{listMinsu.response.body.places[1].placeId}}
...
backend/http/favorite-place.http:114
### 2-1. 민수 목록 (200, 집·회사·헬스장 3건, placeSequence 1·2·3)
```
응답 본문을 `places[].placeId` / `placeSequence` 로 꺼내 쓴다. `place_id`·`place_name` 이 아니다.

> **판정: 무해.** `imageUrl→imageKey` 사고는 **API 필드명**이 바뀐 사건이었고, 이번 건은 **DB 컬럼 매핑**을 명시화한 것이다. 둘은 다르다.
> 추가로, 기준점에서도 이 필드는 `@Id @Column(name = "place_name")` 으로 **이미 `place_name` 컬럼**에 붙어 있었고(`284a950:FavoritePlaceEntity.java:30-32`), `8f54ad4` 가 PK 를 떼면서 애노테이션이 사라진 자리를 `8e7a920` 이 되돌린 것이다. Spring Boot 기본 네이밍 전략(`CamelCaseToUnderscoresNamingStrategy`)이 어차피 `place_name` 으로 변환하므로 **DB 레벨에서도 동작 변화 0** 이다.

### 1-2. 바뀐 계약 — 목록 응답에 `placeId: Long` 이 **추가**됐다

| | 기준점 `284a950` | 현재 `origin/develop` |
|---|---|---|
| `GET /api/v1/users/me/favorite-places` 응답 1건 | `{placeName, roadName, latitude, longitude, placeSequence}` | `{`**`placeId`**`, placeName, roadName, latitude, longitude, placeSequence}` |

근거: `284a950:.../dto/FavoritePlaceResponse.java:5` ↔ `origin/develop:.../dto/FavoritePlaceResponse.java:5`
값의 출처: DB 자동 채번 PK (`FavoritePlaceEntity.java:26-29`, `V19__favorite_place_single_key.sql:3`). 목록은 항상 저장된 행에서 오므로 **null 일 수 없다**.

**앱 쪽 대조:**
```
frontend/data/src/main/kotlin/com/moyeota/data/remote/dto/PlaceDtos.kt:52-66
@Serializable
data class FavoritePlaceListResponse(val places: List<FavoritePlaceItem> = emptyList()) {
    @Serializable
    data class FavoritePlaceItem(
        val placeName: String = "",
        val roadName: String? = null,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val placeSequence: Int = 0,
    )
}
```
`placeId` 를 선언하지 않았다. 디코더는 `NetworkModule.kt:24` 의 `Json { ignoreUnknownKeys = true }` 이고, 이 Json 이 `PlaceApi` 를 만든 Retrofit 컨버터다(`NetworkModule.kt:96`, `:106-110`).

> **판정: 무해(파싱).** 모르는 키 하나 늘어난 것이라 조용히 흘러간다. 크래시도, 오동작도 없다.
> **단, 기능 공백이 생겼다** → §1-3.

**kotlinx 명시적 null 트랩 재점검:** 지시받은 "서버가 null 을 보내기 시작했는가" 를 필드별로 확인했다.
- `placeName` — `FavoritePlace.from()` 이 blank 를 `INVALID_PLACE_NAME`(400) 으로 막는다(`domain/FavoritePlace.java:36`). null 불가.
- `placeSequence` — 서버가 `max+1` 정수로 계산해 넣는다(`FavoritePlaceService.java:40-42`). null 불가. 기준점과 **동일 코드**.
- `roadName` — null 가능. 앱이 이미 `String? = null` 로 방어 중(`PlaceDtos.kt:62`). 변화 없음.
- `placeId` — 앱이 안 읽는다.

→ **새로 null 이 오기 시작한 필드는 없다.**

### 1-3. 바뀐 계약 — 삭제 API 3개 신설, 식별자가 `placeName` → `placeId` 로 **교체**

기준점의 `FavoritePlaceController` 에는 메서드가 **2개**(등록·목록)뿐이었다(`284a950:FavoritePlaceController.java:26-39`). 현재는 **5개**다:

| 메서드 · 경로 | 요청 | 성공 | 실패 | 근거 |
|---|---|---|---|---|
| `POST /api/v1/users/me/favorite-places` | `{placeName, roadName, latitude, longitude}` | 204 | 409 `FAVORITE_PLACE_DUPLICATED` / 409 `FAVORITE_PLACE_LIMIT_EXCEEDED` / 400 `INVALID_PLACE_NAME` | `FavoritePlaceController.java:31-35` |
| `GET /api/v1/users/me/favorite-places` | — | 200 `{places:[…]}` | — | `:39-42` |
| **`DELETE /api/v1/users/me/favorite-places/{placeId}`** | 경로 `Long` | 204 | 400 `INVALID_PLACE_ID` / 404 `FAVORITE_PLACE_NOT_FOUND` | **`:46-50`** |
| **`POST /api/v1/users/me/favorite-places/batch-delete`** | `{"placeIds":[1,2]}` | 204 | 400 `INVALID_PLACE_IDS`(빈 목록) / 400 `INVALID_PLACE_ID`(null 섞임) / 404 (하나라도 내 것이 아니면 **전부 미삭제**) | **`:55-59`**, `FavoritePlaceService.java:66-86` |
| **`DELETE /api/v1/users/me/favorite-places/all`** | — | 204 | 404 `FAVORITE_PLACE_NOT_FOUND`(0건일 때) | **`:63-67`**, `FavoritePlaceService.java:92-98` |

새 에러 코드 3개: `FavoritePlaceErrorCode` 가 아니라 `PlaceErrorCode.java:11-13` 에 추가됐다
(`FAVORITE_PLACE_NOT_FOUND` 404, `INVALID_PLACE_IDS` 400, `INVALID_PLACE_ID` 400).

**식별자 변경 이력** — 지시받은 "경로 파라미터가 바뀌었을 가능성" 은 **사실이다**. 단 전부 PR #238 **내부**에서 일어났고 기준점에는 삭제 API 자체가 없었다:
1. `cdc39da`(10/15) 삭제 API 최초 추가 — 식별자 `placeName`
2. `3b1d6ed`(10/15) 리뷰 반영 — `placeName` 을 경로에서 **쿼리 파라미터**로 (`/` 포함 이름 때문)
3. `8f54ad4`(10/16) 단일키 리팩터 — 식별자를 **`placeId` 경로 파라미터**로 교체

**앱 쪽 대조 — 영향 없음:**
```
frontend/data/src/main/kotlin/com/moyeota/data/remote/PlaceApi.kt:33-38
    @POST("api/v1/users/me/favorite-places")
    suspend fun addFavoritePlace(@Body request: FavoritePlaceRequestDto)

    @GET("api/v1/users/me/favorite-places")
    suspend fun getFavoritePlaces(): FavoritePlaceListResponse
```
삭제 메서드가 없다. `PlaceRepository`(domain)에도 없고(`domain/.../repository/PlaceRepository.kt:7-37`), 화면에도 삭제 어포던스가 없다 — `DestinationScreen.kt:262-266` 은 등록 안내만, `:273` 은 `take(3)` 로 읽기 전용 카드만 그린다. `HomeScreen.kt:210-216` 도 클릭 = 장소 선택뿐이다.

> **판정: 무해(미사용).** 앱은 삭제 API 를 한 번도 쓴 적이 없으므로 식별자 교체에 노출되지 않았다.
> **중간 버전(`placeName` 식별자)을 앱이 연동한 적이 없다는 점이 운이 좋았다.** 만약 10/15~10/16 사이에 연동했다면 `8f54ad4` 에서 404 로 깨졌을 것이다.

**기능 공백(깨짐 아님, 구현 불가):** 지금 앱은 **삭제를 구현할 수 없다.** `placeId` 를 세 계층에서 전부 버리고 있기 때문이다.
필요해지면 아래 네 군데를 고쳐야 한다 (이번 변경 때문에 깨진 게 아니므로 **지금 고치지 않는다**):
- `frontend/data/src/main/kotlin/com/moyeota/data/remote/dto/PlaceDtos.kt:57-66` — `FavoritePlaceItem` 에 `val placeId: Long = 0L` 추가
- `frontend/domain/src/main/kotlin/com/moyeota/domain/model/Place.kt:26-32` — `FavoritePlace` 에 `val id: Long` 추가
- `frontend/data/src/main/kotlin/com/moyeota/data/remote/PlaceMappers.kt:29-35` — `toFavoritePlace()` 에 `id = placeId` 매핑
- `frontend/data/src/main/kotlin/com/moyeota/data/remote/PlaceApi.kt:38` 뒤 — `@DELETE("api/v1/users/me/favorite-places/{placeId}") suspend fun deleteFavoritePlace(@Path("placeId") placeId: Long)`
  + `PlaceRepository`·`RemotePlaceRepository` 시그니처 추가. **compose-builder 에게 사전 통지 필요.**

### 1-4. 🟡 **조용히 틀려짐** — 등록 POST 500 결함(D-4)이 서버에서 고쳐졌는데 앱 문서가 모른다

리포트 22 가 기록한 백엔드 결함:
```
frontend/_workspace/22_api-integrator_authpaths.md:121-130
### 🔴 D-4 (신규) — POST /users/me/favorite-places 가 항상 500
유력 원인: FavoritePlaceEntity 가 @IdClass(FavoritePlaceId.class) 인데
FavoritePlaceId 에 public 무인자 생성자가 없다 … 앱에서 고칠 것은 없다.
```
그 진단은 정확했고, **백엔드가 두 번에 걸쳐 고쳤다**:
1. `3b1d6ed`(10/15) — `FavoritePlaceId` 에 `protected FavoritePlaceId() {}` 추가 (커밋 메시지에 `(#231)` 로 이 이슈를 명시)
2. `8f54ad4`(10/16) — `FavoritePlaceId.java` **파일 자체를 삭제**하고 `@GeneratedValue(IDENTITY)` 단일키로 전환
   (`git ls-tree origin/develop | grep FavoritePlaceId` → 0건)

→ **원인 클래스가 존재하지 않으므로 이 500 은 재발 불가능하다.** 백엔드의 `favorite-place.http:70-96` 도 등록을 204 로 기대하며 시나리오를 짰다.

**그런데 앱 쪽 문서가 거짓을 말하고 있다:**
```
frontend/domain/src/main/kotlin/com/moyeota/domain/repository/PlaceRepository.kt:31-32
 * ⚠ 실서버 현재 상태: 이 POST 는 서버 내부 오류로 **항상 500** 이다(백엔드 결함, 리포트 22 참조).
 * 화면은 실패를 정상적으로 안내만 하면 되고 앱 쪽에 고칠 것은 없다.
```

**이미 prod 에 떠 있다.** `origin/main` 트리 == `origin/develop` 트리(`065ebc24`)이므로 이 수정은 운영 반영 완료다.
→ **지금 사용자 손에 있는 앱 빌드의 ★ 등록이 갑자기 성공하기 시작한다.** 앱을 배포하지 않아도 그렇게 된다.

**"실패를 전제로 짜 놓은 코드" 전수 조사 — 1곳뿐이고, 그것도 주석이다.**
```
grep -rn "항상 500|500 이다|D-4|결함-4|리포트 22|22 보고서|백엔드 결함" --include=*.kt data/ domain/ presentation/ app/
```
→ 히트 8건 중 **즐겨찾기 500 을 전제한 것은 `PlaceRepository.kt:31` 단 하나.** 나머지 7건은 전부 "주체는 Bearer 토큰이 정한다(22 보고서 §2)" 계열의 무관한 인용이다.

전제가 코드로 굳은 흔적이 **없다**는 것도 확인했다:
- **우회·숨김·비활성 없음** — ★ 버튼은 살아서 배선돼 있다: `DestinationScreen.kt:380` `.clickable { onAddFavorite(place) }` → `DestinationRoute.kt:241` `onAddFavorite = viewModel::addFavorite` → `:174-183`.
- **피처 플래그·원격설정 게이팅 없음** — `presentation/.../feature/home/` 에 `ConfigApi`/`remoteConfig`/`featureFlag`/`BuildConfig.` 참조 0건.
- **성공 경로가 이미 올바르다** — `:177` 호출 → `:178` "담았어요" 안내 → `:179` `loadFavorites()` 재조회. 홈도 진입마다 재조회한다(`HomeRoute.kt:68` `LaunchedEffect(Unit) { viewModel.refresh() }`).

> **판정: 조용히 틀려짐 (문서·QA 전제). 코드는 고칠 것이 없다.**
> 런타임 동작은 이미 맞다 — 등록이 성공하면 안내가 뜨고 목록이 갱신된다. **앱 코드 수정 없이 기능이 그냥 살아난다.**
> **위험은 세 가지다:**
> 1. 다음 사람이 이 KDoc 을 읽고 "즐겨찾기 등록은 어차피 안 되는 기능" 으로 취급한다.
> 2. **QA 가 "등록 실패" 를 정상으로 통과시킨다.** 이제는 등록 성공이 기대값이고, 실패하면 그게 새 결함이다.
> 3. **성공 경로가 한 번도 실기 검증된 적이 없다.** 지금까지 항상 500 이었으므로 `:178`→`:179` 경로(안내 문구 + 목록 즉시 갱신)는 코드로만 맞는 상태다. **실기 확인 1순위.**
>
> **고칠 곳 (문서 1곳 + 주석 1곳, 동작 변경 없음):**
> - `frontend/domain/src/main/kotlin/com/moyeota/domain/repository/PlaceRepository.kt:31-32` — ⚠ 블록 삭제. 실패 조건을 실제 서버 코드 기준으로 교체: `409 FAVORITE_PLACE_DUPLICATED`(같은 이름, `FavoritePlaceService.java:34-36`) / `409 FAVORITE_PLACE_LIMIT_EXCEEDED`(10개 초과, `:38`) / `400 INVALID_PLACE_NAME`(빈 이름)
> - `frontend/presentation/src/main/kotlin/com/moyeota/presentation/feature/home/DestinationRoute.kt:181` — 주석 `// 서버 400: 이름 중복 · 10개 초과` 가 틀렸다. 중복·초과는 **409 CONFLICT** 다(`PlaceErrorCode.java:9-10`). 사용자 문구(`:182`)는 두 사유를 다 덮으므로 그대로 둬도 된다.

### 1-5. 목록 정렬 — 앱의 방어 코드가 막아 줬다

`8f54ad4` 가 PK 를 `(user_id, place_name)` → `place_id` 로 바꾸면서, `ORDER BY` 가 없는 `findByUserId`(`FavoritePlaceJpaRepository.java:12`)의 **행 반환 순서가 바뀔 수 있다**(이름순 → 채번/물리 순). 서버는 정렬을 보장하지 않는다.

앱은 이미 자체 정렬한다:
```
frontend/data/src/main/kotlin/com/moyeota/data/repository/RemotePlaceRepository.kt:28-32
    // 서버가 정렬을 보장한다는 언급이 없어(placeSequence 만 내려줌) 앱에서 순서를 확정한다.
    override suspend fun getFavoritePlaces(): List<FavoritePlace> =
        api.getFavoritePlaces().places.map { it.toFavoritePlace() }.sortedBy { it.sequence }
```
화면에서도 한 번 더 정렬한다(`DestinationRoute.kt:83`, `HomeRoute.kt:34`).

> **판정: 무해.** 이 방어가 없었으면 "자주 가는 곳" 카드 3개의 순서가 조용히 바뀌는 전형적인 사일런트 버그였다. 유지할 것.

### 1-6. 기준점 대비 **안 바뀐** 것 (대조 완료, 조치 불필요)

- 등록 요청 본문: `{placeName, roadName, latitude, longitude}` 동일 → `PlaceDtos.kt:43-49` 그대로 유효
- 두 경로 문자열: `api/v1/users/me/favorite-places` 동일 → `data/src/test/.../AuthenticatedPathContractTest.kt:193-197` 통과 유지
- 인증 주체: `@CurrentUser Long userId`, 쿼리 `userId` 없음 — 동일
- 최대 10개 제한, `placeSequence = max+1` 규칙 — 동일
- 중복 판정 기준: 기준점 `existsByUserIdAndPlace(userId, placeName)` → 현재 `list.stream().anyMatch(placeName equals)`. **구현만 바뀌고 기준(= 같은 유저의 같은 이름)과 응답 코드(409)는 동일.**

### 1-7. 판정 요청 건 — **즐겨찾기 삭제 UI 는 "없다". 결론은 「화면부터 필요하다」**

두 가설 중 어느 쪽인지 전수 확인했다.

**가설 A(비활성/누락된 삭제 UI 가 있다) — 기각.** 삭제 어포던스가 **한 개도 없다**:
```
grep -rni "favorite.*delete|delete.*favorite|즐겨찾기.*삭제|삭제.*즐겨찾기|onFavoriteDelete|removeFavorite|unfavorite" \
  --include=*.kt presentation/ data/ domain/
```
→ 히트 **1건**, 그리고 그것은 사용자 기능이 아니다:
```
frontend/domain/src/main/kotlin/com/moyeota/domain/repository/AuthRepository.kt:168
 * - 커밋 후 비동기로 **참여 중인 채팅방 전부 나가기**와 **즐겨찾기 삭제**
```
→ **회원 탈퇴 시 서버가 수행하는 카스케이드 설명**이다. 백엔드 `FavoritePlaceUserDeletionListener` 쪽 동작이고, 앱이 호출하는 즐겨찾기 삭제 API 와 무관하다.

**가설 B(UI 자체가 없다) — 확정.** 즐겨찾기 관련 UI 는 전부 **읽기 전용 + 등록 전용**이다:

| 위치 | 어포던스 | 동작 |
|---|---|---|
| `DestinationScreen.kt:380-383` | 검색 결과 행의 ★ | **등록** (`onAddFavorite`) |
| `DestinationScreen.kt:273-288` | 「자주 가는 곳」 카드 3개 | `.clickable` = **장소 선택**. ★ 는 장식(`:288` `StarIcon()`, 클릭 없음) |
| `HomeScreen.kt:210-216` | 홈 즐겨찾기 행 | `onClick = { onFavoritePlaceClick(place) }` = **도로명으로 검색 이동**(`HomeRoute.kt:80`) |

해당 영역에 `combinedClickable`·`onLongClick`·`IconButton`·`Close`/`Delete` 아이콘이 **전무**하다(두 구간 코드 추출 확인). 편집 모드·스와이프 삭제·전체 삭제 메뉴도 없다.

> **판정: 후자 — 「화면부터 필요하다」.**
> 서버는 삭제 3종(단건·다건·전체)을 prod 에서 이미 제공하고 있고, **앱에는 그걸 부를 자리가 애초에 없다.** 따라서 이 변경은 **지금 깨진 것이 아니라 "쓰지 않고 있는 신규 능력"** 이다.
>
> **붙이려면 순서가 이렇다 (이번 리포트 범위 밖 — 지시받은 대로 코드는 안 고쳤다):**
> 1. **compose-builder 선행** — 15 목적지의 「자주 가는 곳」 카드(또는 홈 행)에 삭제 어포던스 설계. 단건 삭제만으로 충분한지, 편집모드+다건(`batch-delete`)·전체(`/all`)까지 쓸지를 먼저 정해야 API 연동 범위가 정해진다.
> 2. **api-integrator 후행** — §1-3 의 4파일(`PlaceDtos.kt:57-66` 에 `placeId` 수신, `Place.kt:26-32` 도메인에 `id`, `PlaceMappers.kt:29-35` 매핑, `PlaceApi.kt:38` 뒤 `@DELETE`) + `PlaceRepository`/`RemotePlaceRepository` 시그니처.
> 3. 삭제 성공 후 `loadFavorites()` 재조회 필요(등록과 동일 패턴, `DestinationRoute.kt:179`).
>
> **식별자 전달 경로가 지금 끊겨 있다는 점이 핵심 제약이다** — 서버는 `placeId`(int64)를 내려주는데 앱 DTO·도메인이 둘 다 버리므로, 화면을 먼저 만들어도 지울 대상을 지정할 수 없다. 1번과 2번은 **같은 작업 단위로 묶어야 한다.**

---

## 2. 채팅 (PR #253 `feature/chat-room-left-unsubscribe`, `f758cbc`)

### 2-1. 바뀐 계약 — 서버가 나간 사람의 방 구독을 **강제로 끊는다**

```
backend/src/main/java/.../chat/common/redis/ChatRedisSubscriber.java:55-63
    private void handleRoomLeft(String payload) {
        ChatRoomLeftResult result = objectMapper.readValue(payload, ChatRoomLeftResult.class);
        subscriptionCleaner.unsubscribeRoom(result.userId(), result.chatRoomId());   // ← 신설
        messagingTemplate.convertAndSendToUser(..., ROOM_LEFT_DESTINATION, ...);
    }
```
```
backend/src/main/java/.../chat/common/session/ChatSubscriptionCleaner.java:17, 22-38
    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat-rooms/";
    public void unsubscribeRoom(Long userId, Long chatRoomId) {
        SimpUser user = userRegistry.getUser(String.valueOf(userId));
        if (user == null) return;                       // 이 서버에 세션 없으면 아무것도 안 함
        String room = ROOM_DESTINATION_PREFIX + chatRoomId;
        for (SimpSession session : user.getSessions())
            for (SimpSubscription subscription : session.getSubscriptions())
                if (isRoom(subscription.getDestination(), room))
                    messagingTemplate.getMessageChannel().send(unsubscribe(session.getId(), subscription.getId()));
    }
    private static boolean isRoom(String destination, String room) {
        return destination.equals(room) || destination.startsWith(room + "/");
    }
```

**죽이는 대상**: `/sub/chat-rooms/{id}` 와 그 하위(`/location`, `/members`). 번호가 겹치는 다른 방(`/sub/chat-rooms/120`)은 건드리지 않는다 — 백엔드 테스트가 이를 고정했다(`backend/src/unitTest/.../ChatSubscriptionCleanerTest.java:54-74`).

**트리거 경로**: `DELETE /api/v1/chat-rooms/{id}/users`(`ChatRoomUserController.java:46-54`) 또는 파티 이탈(`PartyMemberLeftEvent` → `ChatRoomMatchingListener.java:28-30` → `MatchingChatRoomSteps.java:63-65`) → `ChatRoomLeftEvent` → Redis `ROOM_LEFT` → 위 핸들러.

### 2-2. 지시받은 핵심 질문 — **이중 처리는 없다. 앱은 UNSUBSCRIBE 를 보내지 않는다.**

앱의 소켓 수명주기 전체를 읽었다:
```
frontend/data/src/main/kotlin/com/moyeota/data/remote/chat/ChatSocket.kt:152-166
    val messages = session.subscribeText("/sub/chat-rooms/$chatRoomId")
    emit(RoomSignal.Connected)
    messages.collect { … }
    …
} finally {
    live = null
    runCatching { session.disconnect() }       // ← 구독 해제가 아니라 세션 전체 종료
}
```
- 앱이 구독하는 목적지는 **딱 둘**: `/sub/chat-rooms/{id}`(`:152`) 와 `/user/queue/errors`(`:28`, `:171`).
- 방을 벗어날 때 `UNSUBSCRIBE` 프레임을 **보내지 않고** DISCONNECT 로 세션째 닫는다(`:165`).
- 화면이 STOP/dispose 되면 `stopPolling()` 이 `socketJob` 을 취소하고(`frontend/presentation/.../chat/ChatRoute.kt:433-438`), 그 취소가 위 `finally` 를 타 `disconnect()` 로 간다. 배선은 `ChatRoute.kt:771-778`:
```
    LifecycleStartEffect(room.id) {
        viewModel.onScreenStart(room.id, knownMuted)
        …
        onStopOrDispose { viewModel.stopPolling(); … }
    }
```
- 나가기 성공 → `_leftRoom = true`(`ChatRoute.kt:553-561`) → `LaunchedEffect(leftRoom) { onLeaveChat() }`(`:780-781`) → 네비게이션 → STOP → 소켓 종료.

> **판정: 무해.** 앱이 하던 "구독 해제" 가 애초에 없었으므로 **겹치지 않는다.**
> 설령 겹쳐도 안전하다: 브로커가 `enableSimpleBroker` 이고(`backend/.../chat/common/config/WebSocketConfig.java:35`), `SimpleBrokerMessageHandler` 의 UNSUBSCRIBE 는 없는 구독 id 에 대해 no-op 다 — 외부 브로커 릴레이가 아니라 에러 프레임이 돌아오지 않는다.

### 2-3. 앱이 쓰는 두 구독은 사정권 밖이다

| 앱이 구독하는 목적지 | `isRoom()` 매칭? | 결과 |
|---|---|---|
| `/sub/chat-rooms/{id}` | ✅ 매칭 | 나간 뒤에만 끊긴다 (그 시점 앱은 이미 화면을 떠났다) |
| `/user/queue/errors` | ❌ | 그대로 유지 |

서버가 나가기 알림을 보내는 `/queue/room-left`(`ChatRedisSubscriber.java:24`)는 **앱이 구독하지 않는다.** 백엔드 테스트도 이 목적지가 해제 대상이 아님을 고정해 뒀다(`ChatSubscriptionCleanerTest.java:59`, `:72-73`).

> **판정: 무해.** 앱이 받던 프레임이 끊기는 경우는 "이미 나간 방" 하나뿐이고, 그 상황에서 앱은 네비게이션 중이다.
> **행동 변화의 실질:** `f758cbc` **이전에는** 나간 사람의 스테일 구독으로 서버가 계속 메시지를 밀고 있었다(누출). 이제 안 민다. 앱 입장에서는 **보던 화면을 떠난 뒤 오던 쓸모없는 프레임이 사라지는 것**이라 손해가 없다.

### 2-4. 🟡 발견한 **기존** 취약점 (이번 변경이 만든 게 아니다 — 참고용)

`ChatSocket` 은 SUBSCRIBE 가 **수락됐는지 확인하지 않고** `Connected` 를 낸다:
```
frontend/data/src/main/kotlin/com/moyeota/data/remote/chat/ChatSocket.kt:152-153
    val messages = session.subscribeText("/sub/chat-rooms/$chatRoomId")
    emit(RoomSignal.Connected)
```
STOMP SUBSCRIBE 는 응답이 없으므로 이건 "프레임을 썼다" 는 뜻일 뿐이다. 그리고 `Connected` 는 폴링을 **끈다**:
```
frontend/presentation/.../chat/ChatRoute.kt:416-417, 447
    ChatRealtimeEvent.Connected -> { realtimeAlive = true; fetchNew(roomId) }
    …
    if (realtimeAlive) continue          // 폴링 건너뜀
```
→ 서버가 구독을 거절했거나(참여자 아님) 제거했는데 **TCP 연결은 살아 있는** 상태에서는, `realtimeAlive` 가 true 로 굳어 폴링이 멈추고 대화가 조용히 정지한다. 서버의 거절 통보는 `/user/queue/errors` 로 오지만 앱은 **로그만 찍는다**(`ChatSocket.kt:173`).

**이번 변경이 이 경로를 새로 열지는 않았다**고 판단한 근거:
- 나가기 직후엔 앱이 화면을 떠나므로 노출 창이 수십 ms 다.
- "나간 방에 계속 머무는" 상황은 앱이 구조적으로 만들지 않는다 — `ChatRoute.kt:818` 주석대로 **진행 중인 내 방 채팅에는 「나가기」 버튼을 두지 않고**, 끝난 방에서 나가면 즉시 이탈한다.
- 30분 후 자동 종료(`backend/.../matching/party/CompletedPartySweeper.java:26-36`)는 `PartyService.expire()` 가 `PartyClosedEvent` 만 발행하고(`PartyService.java:203-214`) **`PartyMemberLeftEvent` 를 발행하지 않는다** → 채팅방 멤버를 빼지 않으므로 강제 해제가 걸리지 않는다.

> **판정: 무해(이번 변경 기준). 다만 잠재 취약점으로 기록한다.**
> 지금 고칠 필요는 없다. 만약 향후 "채팅이 멈췄는데 에러도 없다" 류 제보가 오면 **첫 번째로 볼 곳이 `ChatRoute.kt:416-417` + `:447` 의 `realtimeAlive` 게이트**다. 방어하려면 `/user/queue/errors` 수신 시 `realtimeAlive = false` 로 떨어뜨려 폴링을 되살리는 게 가장 작은 수정이다(`ChatSocket.kt:170-174` → `RoomSignal` 에 `Rejected` 추가).

### 2-5. 참고 — 백엔드 내부 관찰 (앱 무관)

주입된 UNSUBSCRIBE 는 클라이언트 인바운드 채널이 아니라 brokerChannel 로 들어가므로, `SessionUnsubscribeEvent` 가 발행되지 않아 **`SimpUserRegistry` 에는 구독이 유령으로 남는다**(브로커에서는 제거됨). 같은 room-left 가 재발행되면 같은 id 로 UNSUBSCRIBE 를 또 보내지만 no-op 이다. **앱에서 관측 불가 · 조치 불필요.** 백엔드 팀에 공유만 할 가치는 있다.

---

## 3. 결제 (PR #246 `feature/portone-env-config`)

### 3-1. 바뀐 계약 — **없다. 설정 키 2줄이 전부다.**

PR #246 의 전체 diff (`git diff c109b33^1 c109b33`, 커밋 `b818dcd` 1건):
```
backend/src/main/resources/application.yaml
 portone:
   api:
     secret: ${PORTONE_SECRET:}
+  webhook:
+    secret: ${PORTONE_WEBHOOK_SECRET:}
```
`1 file changed, 2 insertions(+)`. Java 소스 변경 0.

### 3-2. 지시받은 질문 — "응답/흐름이 바뀌었나?" → **바뀔 응답이 존재하지 않는다.**

- **백엔드에 결제 코드가 한 줄도 없다.** `git grep -l -i "portone|port_one|포트원" origin/develop` → **`application.yaml` 단 1개 파일**. 컨트롤러·서비스·엔티티·웹훅 핸들러 전무.
- `payment` 패키지 자체가 `origin/develop` 에 없다 (`git ls-tree -r origin/develop | grep -i payment` → 0건). 결제 작업은 머지되지 않은 `feature/payment` 브랜치에 있다.
- 즉 `PORTONE_WEBHOOK_SECRET` 은 **아직 아무도 읽지 않는 자리 예약**이다.

**앱 쪽 대조 — 결제 기능이 아예 없다:**
- 결제 화면 28~32 가 삭제됐고, 그 사실을 테스트가 **능동적으로 고정**하고 있다:
```
frontend/presentation/src/test/.../onboarding/ProductClaimsTest.kt:83, 91-92
 * **결제·정산이 없다.** 28~32 결제 화면 5개가 삭제됐고 요금 확정·결제·영수증 API 가 전무하다.
        listOf("정산", "결제", "카드", "자동으로 나눠").forEach { forbidden ->
            assertFalse("없는 결제·정산을 약속한다: $forbidden", preSignupCopy.contains(forbidden))
```
- 마이페이지에도 결제 수단 메뉴가 없음을 고정(`frontend/presentation/src/test/.../mypage/MyPageMenuRulesTest.kt:213`).
- `RideCompleteScreen.kt:103` 은 "「내가 낸 돈」이 아니라 「1인 부담」" 이라고 명시 — 결제 기능이 없어서.

> **판정: 무해.** 환경변수 설정 변경이고 양쪽 모두 결제 기능이 없다. **포트원이 "환경변수로 빠진 것뿐" 이 맞다.** 앱이 보는 응답·흐름 변화 0.

---

## 4. 기타 — `e2f7014` `@Scheduled` 스레드 풀

### 4-1. 변경 내용
```
backend/src/main/java/team/codingforest/moyeota/_config/SchedulingConfig.java (신규 15줄)
@Configuration
public class SchedulingConfig {
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
}
```
`spring.task.scheduling.pool.size: 4` 는 **기준점에도 이미 있었다**(`application.yaml:39-42`). 다른 `TaskScheduler` 빈들(`WebSocketConfig.heartbeatScheduler()` pool=1, SSE 전용 스케줄러) 때문에 Boot 의 `taskScheduler` 자동설정이 뒤로 물러나 그 설정이 `@Scheduled` 에 적용되지 않던 것을 명시 빈으로 되살렸다.

영향을 받는 `@Scheduled` 작업 (`git grep -n "@Scheduled" origin/develop`):
`ChatReadBuffer`(5초, 읽음 플러시) · `EventResubmitter`(5초) · `MatchingSweeper`(30초, 배차) · `CompletedPartySweeper`(10분) · `DriverRetentionSweeper`·`UserDeletionSweeper`(일 1회).
`PartySseRegistry` 는 `scheduler = PartySseSchedulerConfig.SCHEDULER` 로 전용 스케줄러를 지정해 **영향 없음**(`PartySseRegistry.java:102`).

### 4-2. 판정

> **판정: 무해.** 요청/응답 shape·경로·에러 코드 변화 0. HTTP·STOMP·SSE 계약 어디도 건드리지 않는다.
> 앱에 나타날 수 있는 차이는 **타이밍 신뢰도뿐이고, 전부 개선 방향**이다:
> - 안 읽은 메시지 배지 플러시(`ChatReadBuffer`, 5초)와 배차 스윕(`MatchingSweeper`, 30초)이 한 스레드를 두고 경쟁하지 않는다 → 배지·배차 타임아웃이 늦게 반영되던 흔들림이 줄어든다.
> - 긴 스윕이 WebSocket heartbeat 스케줄러를 붙잡고 있을 가능성이 사라진다 → 채팅 소켓이 이유 없이 재연결되는 빈도가 줄어들 수 있다.
>
> `@Scheduled(fixedDelay)` 는 자기 자신과 겹치지 않고, 서버 여러 대는 `@SchedulerLock` 이 막는다(`SchedulerLockConfig.java`) → 병렬화로 인한 중복 실행 위험 없음.
> **앱 쪽 수정 사항 없음.** 지시받은 대로 "확인만" 했다.

---

## 5. 미확인 (추측하지 않고 남긴 것)

**실측으로 해소된 것 (이전 판 대비):** 경로·메서드·식별자 타입·JSON 키명·에러코드는 전부 운영 OpenAPI 로 확정됐다. `/all` 과 `/{placeId}` 가 **둘 다 별개 경로로 등록돼 있음**도 스펙에서 확인했다(이전 M-5 해소). 삭제 API 가 prod 에 실재한다는 것도 확정이다.

| # | 항목 | 왜 미확인인가 | 확인 방법 |
|---|---|---|---|
| M-1 | 등록 POST 가 **실제 호출에서** 204 를 주는가 | 원인 클래스(`FavoritePlaceId`)가 삭제돼 재발 불가는 소스로 확정, 운영 스펙도 204 를 문서화. 다만 **인증 토큰이 필요해 실제 등록을 돌리지 않았다** — prod 에 쓰기 요청을 보내지 않는다는 원칙 때문이다. **"고쳐졌다" 는 소스+스펙 기준 판단이다.** | 에뮬레이터 실기(로그인 후 15 목적지 ★) 또는 `backend/http/favorite-place.http` 1-1~2-1 을 **로컬/dev** 에서 실행 |
| M-2 | 목록 응답 본문에 `placeId` 값이 실제로 담겨 오는가 | 스키마에는 있다(실측). 본문 실물은 토큰이 없어 못 봤다. `placeSequence`/`placeName` 의 널 여부도 OpenAPI 가 `required`·`nullable` 을 표기하지 않아 **스펙으로는 판단 불가** — 서버 소스로만 판단했다(§1-2) | 실기 로그인 후 `GET` 응답 본문 1건 확인 |
| M-3 | `@Scheduled` 가 **이전에** 정확히 어느 스케줄러에서 돌았는가 | 후보 `TaskScheduler` 빈이 여러 개라 Spring 의 해석 결과(by-type 실패 → by-name → 로컬 단일 스레드 폴백)를 소스만으로 단정할 수 없다. **다만 어느 경로였든 앱 계약 영향은 0 이므로 판정은 바뀌지 않는다.** | 서버 로그의 스케줄러 스레드 이름 확인 |
| M-4 | `V19` 마이그레이션이 실 DB(Postgres)에서 통과하는가 | `ALTER TABLE favorite_place DROP CONSTRAINT favorite_place_pkey` 는 Postgres 자동 생성 제약명에 의존한다. **prod 가 떠 있고 OpenAPI 가 200 이며 `ddl-auto: validate`(`application-dev.yaml`)가 엔티티↔스키마 불일치 시 기동을 실패시키므로, 통과했다고 볼 근거는 충분하다.** 마이그레이션 로그는 못 봤다 | 백엔드 팀 확인 사항 (앱 영향 없음) |
| M-5 | 중복·10개초과가 **실제로** 409 로 오는가 | 소스는 409(`PlaceErrorCode.java:9-10`), 운영 OpenAPI 는 409 를 **문서화하지 않았다**(§1-0 불일치 메모). 실제 호출로 확인하지 않았다. **앱은 모든 예외를 같은 문구로 덮으므로 어느 쪽이든 동작 영향 없음.** | 실기에서 같은 이름 2회 등록 |

---

## 6. 앱 변경 권고 (코드는 아직 고치지 않았다)

| 우선 | 대상 | 조치 | 근거 |
|---|---|---|---|
| **1** | `frontend/domain/src/main/kotlin/com/moyeota/domain/repository/PlaceRepository.kt:31-32` | "항상 500" ⚠ 블록 삭제 → 실제 실패 조건(409 중복 / 409 10개 초과 / 400 빈 이름)으로 교체 | §1-4 |
| **2** | `frontend/presentation/src/main/kotlin/com/moyeota/presentation/feature/home/DestinationRoute.kt:181` | 주석 `// 서버 400: 이름 중복 · 10개 초과` → `// 서버 409: 이름 중복 · 10개 초과 (운영 OpenAPI 는 409 미문서화 — 소스 기준)`. 사용자 문구(`:182`)는 유지 | §1-0, §1-4 |
| **3** | QA 체크리스트 | "즐겨찾기 등록 실패는 정상" 전제를 **제거**. 이제 등록 성공이 기대값이고 실패가 결함이다. **성공 경로는 한 번도 실기 검증된 적이 없으므로 1순위** | §1-4 |
| 4 | 백오로그 (신규 기능) | 즐겨찾기 **삭제 화면 + 연동**. 서버는 prod 에서 준비됐고 앱에 자리가 없다 → **compose-builder(화면) + api-integrator(placeId 수신·DELETE) 묶음 작업** | §1-7 |
| — | DTO·API·매퍼·화면 | **변경 불필요.** 즐겨찾기 파싱·채팅 소켓·결제 모두 현행 코드가 운영 서버와 맞는다(실측 대조 완료) | §1-0, §1-2, §2, §3 |

**지금 당장의 Repository 시그니처 변경 없음** → compose-builder 에게 통지할 경계면 변화 **현재는 없다.**
단 §1-7 의 삭제 기능을 착수하면 `PlaceRepository` 에 `deleteFavoritePlace(id: Long)`(+필요시 batch/all) 가 추가되고 `domain FavoritePlace` 에 `id` 가 붙는다 — **도메인 모델 필드 추가이므로 화면 쪽에도 영향이 있다. 그때는 착수 전에 사전 통지한다.**

## 7. qa-verifier 교차 검증 요청

| 엔드포인트 | 앱 화면 | 기대 |
|---|---|---|
| `GET /api/v1/users/me/favorite-places` | 14 홈 「자주 가는 곳」, 15 목적지 | `placeId` 가 섞여 와도 목록이 그려지고 sequence 순 정렬 유지 (`ignoreUnknownKeys` 검증) |
| `POST /api/v1/users/me/favorite-places` | 15 목적지 검색결과 ★ | 🔴 **1순위.** 204 → "담았어요" + 「자주 가는 곳」 즉시 갱신 + 홈 복귀 시에도 반영. **이전엔 항상 500 이라 이 성공 경로가 실기에서 한 번도 돈 적이 없다** |
| 같은 이름 재등록 | 15 목적지 | 409 → "담지 못했어요 (중복이거나 10개를 넘었을 수 있어요)". **실제 상태코드가 409 인지 함께 기록** (운영 OpenAPI 미문서화 — §1-0) |
| 11개째 등록 | 15 목적지 | 409 → 같은 문구 |
| `GET` 목록 | 14 홈 · 15 목적지 | 응답 본문에 `placeId` 가 실제로 들어오는지 1건 캡처 (M-2 해소용). 파싱은 깨지지 않아야 함 |
| `DELETE /api/v1/chat-rooms/{id}/users` | 24 채팅방 「나가기」 | 200 → 목록 복귀. **서버 강제 구독 해제와 앱 DISCONNECT 가 겹쳐도 에러 토스트·ANR 없음** |
| 나간 방 재진입 시도 | 24 채팅방 | 403 `CHAT_NOT_PARTICIPANT` → `onNotParticipant` 경로로 정상 이탈 |

콜드부트 에뮬레이터 전제(MEMORY 기록대로).
