# 15. 역지오코딩(좌표 → 주소) 연동 — api-integrator

## 연동 엔드포인트

| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/v1/places/reverse?latitude={lat}&longitude={lng}` | 좌표 → 주소 1건 |

백엔드: `place/interfaces/PlaceSearchController.reverse` → `ReverseGeocodingApplication.findAddress` → `NaverReverseGeoCodingClient`(네이버 reversegeocode, orders=roadaddr,addr)

## 실제 응답 shape (localhost:8080 **실서버 curl 실측**, 2026-09-01)

정상 (35.2313, 129.0838):
```json
{"address":"부산광역시 금정구 부산대학로63번길 2 부산대학교","roadAddress":"부산광역시 금정구 부산대학로63번길 2 부산대학교","jibunAddress":"부산광역시 금정구 장전동 40"}
```

도로명 없는 좌표 (35.0, 128.0):
```json
{"address":"경상남도 사천시 서포면 자혜리 1230-17","roadAddress":null,"jibunAddress":"경상남도 사천시 서포면 자혜리 1230-17"}
```

주소 없는 좌표 = 바다 (34.8, 126.3) → **404**:
```json
{"code":"ADDRESS_NOT_FOUND","message":"해당 좌표의 주소를 찾을 수 없습니다."}
```

한국 영역 밖 (10.0, 100.0) → **400**:
```json
{"code":"INVALID_COORDINATES","message":"좌표가 올바르지 않습니다."}
```
서버가 위도 33~39, 경도 124~132 범위를 먼저 검사한다(`ReverseGeocodingApplication`의 MIN/MAX 상수).

`address`는 서버 `Address.display()` = `roadAddress != null ? roadAddress : jibunAddress`. **앱에서 다시 조합하지 않는다.**

## 확정 Repository 시그니처 (compose-builder 공유용)

```kotlin
// domain/repository/PlaceRepository.kt
suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseAddress?

// domain/model/Place.kt
data class ReverseAddress(
    val address: String,      // 표시용. 서버가 도로명 우선으로 조합해 준 값. 항상 non-blank
    val roadAddress: String?, // 도로명 없는 좌표에서 null
    val jibunAddress: String?,
)
```

### 호출측 계약 — null 의 의미
- **`null` = 주소가 없는 좌표(바다 등). 예외가 아니다.** 핀을 아무 데나 찍는 건 정상 유저 행동이므로 에러 스낵바/에러 화면을 띄우지 말 것. 주소 표시 갱신만 생략하고 직전 값을 유지하면 된다.
- 그 외 실패는 기존 컨벤션대로 **예외(`retrofit2.HttpException`)** 로 던진다:
  - 400 `INVALID_COORDINATES` — 한국 영역 밖. 지도를 국내로 제한하면 실질적으로 발생하지 않는다. 발생 시 "국내 주소만 조회할 수 있어요" 정도의 메시지 권장.
  - 502 `REVERSE_GEOCODE_FAILED` — 네이버 API 실패. 재시도 대상.
- 액션이 아닌 조회라 성공 후 refresh 대상 없음. 핀 이동마다 호출되므로 **디바운스는 호출측(ViewModel) 책임**이다 — Repository는 매 호출마다 서버로 나간다.

## 변경 파일

- `data/src/main/kotlin/com/moyeota/data/remote/PlaceApi.kt` — `reverseGeocode` 추가. 404를 null로 다뤄야 해서 반환 타입만 `Response<AddressResponseDto>`(다른 메서드와 다름)
- `data/src/main/kotlin/com/moyeota/data/remote/dto/PlaceDtos.kt` — `AddressResponseDto`(3필드 전부 nullable + 기본값)
- `data/src/main/kotlin/com/moyeota/data/remote/dto/ErrorDtos.kt` (신규) — `ApiErrorDto(code, message)`, 백엔드 공통 `ErrorResponse` 대응
- `data/src/main/kotlin/com/moyeota/data/remote/PlaceMappers.kt` — `Response<AddressResponseDto>.toReverseAddressOrNull()`, `AddressResponseDto.toReverseAddressOrNull()`
- `data/src/main/kotlin/com/moyeota/data/repository/RemotePlaceRepository.kt` — 구현 1줄
- `domain/.../model/Place.kt`, `domain/.../repository/PlaceRepository.kt`
- `data/src/test/.../PlaceMappersTest.kt` — 역지오코딩 5케이스 추가

## 설계 노트

1. **404 판별은 상태 코드만 보지 않는다.** `code == "ADDRESS_NOT_FOUND"` 까지 확인해야 null이 된다. 경로 오타로 스프링이 뱉는 404까지 null로 삼키면 연동 실패가 조용히 묻히기 때문. 테스트로 고정해 뒀다.
2. **2xx인데 세 필드가 다 비면 null.** 네이버가 roadaddr/addr 어느 쪽도 못 채우면 서버가 `Address(null, null)` → `address=null`을 내려보낼 수 있다(`NaverReverseGeoCodingClient`는 results가 비지 않으면 무조건 `Optional.of`). 표시할 게 없으므로 404와 같은 취급으로 묶어 호출측 분기를 한 갈래로 유지.
3. `DummyPlaceRepository`는 존재하지 않는다(더미는 `DummyRideRepository` 뿐). `PlaceRepository` 구현체는 `RemotePlaceRepository` 하나라 더미 갱신 대상 없음.

## 검증

- 실서버 검증: **완료** (localhost:8080, 정상/roadAddress null/404/400 4케이스 curl 실측)
- `./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin` — BUILD SUCCESSFUL, `PlaceMappersTest` 11 tests / 0 failures
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- presentation 연결은 범위 밖(compose-builder 담당)
