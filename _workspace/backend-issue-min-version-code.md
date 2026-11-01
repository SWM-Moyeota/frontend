# 백엔드 이슈 — `GET /api/v1/config` 에 최소/권장 버전 추가

작성: 2026-11-01 · **제출 완료 → [SWM-Moyeota/Backend#277](https://github.com/SWM-Moyeota/Backend/issues/277)**
근거: 백엔드 소스 읽기만 했고 코드는 건드리지 않았다.
제출 전 같은 요청이 있는지 확인했다 — 기존 이슈 100건에 `config`·`버전`·`업데이트`·`version` 매칭 0건.

---

## 제목 (제출한 것)

```
GET /api/v1/config 에 minVersionCode 추가 — 앱이 나간 뒤에는 강제 업데이트를 영구히 걸 수 없다
```

레포의 기존 이슈 제목 형식(「문제 — 근거」)에 맞췄다.

## 본문

```markdown
## 요청

`GET /api/v1/config` 응답에 정수 필드 두 개를 더해 주세요. **둘 다 선택(없어도 됨)입니다.**

```json
{
  "taxiEnabled": false,
  "minVersionCode": 26110102,
  "recommendedVersionCode": 26110102
}
```

| 필드 | 형식 | 뜻 |
|---|---|---|
| `minVersionCode` | Integer, 생략 가능 | 이 값보다 낮은 `versionCode` 의 앱은 **빠져나갈 수 없는 업데이트 화면**을 띄우고 더 진행하지 않습니다 |
| `recommendedVersionCode` | Integer, 생략 가능 | 낮으면 **막지 않고** 한 번 알리기만 합니다 |

값은 Android `versionCode` 와 같은 정수입니다. 앱은 `YYMMDD*100+N` 형식을 쓰고 있어
현재 프로덕션 빌드가 `26110102` 입니다.

## 왜 지금 필요한가

**이 문은 한 번 닫히면 다시 열 수 없습니다.**

앱이 스토어에 나간 뒤에는 그 버전을 쓰는 사람들에게 업데이트를 강제할 방법이 없습니다.
나중에 서버가 `minVersionCode` 를 내려줘도 **그 필드를 모르는 빌드는 조용히 무시**합니다
(앱의 JSON 파서가 `ignoreUnknownKeys = true` 입니다). 그래서 서버 계약이 깨지는 변경을
해야 하는 날, 그 사용자들은 영구히 고장난 앱을 들고 있게 됩니다.

오늘 올라가는 프로덕션 빌드(`26110102`)**부터는 이 두 필드를 읽습니다.** 서버가 값을
주기 시작하는 날부터 모든 설치본에 게이트가 걸립니다. 반대로 서버가 끝까지 안 주면
앱의 그 코드는 영원히 쓰이지 않습니다.

## 지금 상태

소스 기준으로 응답 필드가 하나입니다.

- `_config/AppConfigResponse.java:3` — `public record AppConfigResponse(boolean taxiEnabled)`
- `_config/AppConfigController.java:19` — `@Value("${moyeota.taxi.enabled:true}")`
- `_config/AppConfigController.java:13` — `@SecurityRequirements` (인증 불필요)

## 앱 쪽은 이미 끝나 있습니다

| 자리 | 내용 |
|---|---|
| `data/.../dto/AppConfigDtos.kt:20` | 두 필드를 **기본값 null** 로 받습니다. 안 와도 역직렬화가 깨지지 않습니다 |
| `presentation/.../core/UpdateGate.kt:43` | 판정 순수 함수. 차단이 권장보다 먼저 |
| `presentation/.../core/MainNavGraph.kt:219` | `NavHost` 를 **세우기 전에** 판정합니다. 뒤로가기로 빠져나갈 화면이 없습니다 |
| `presentation/src/test/.../UpdateGateTest.kt` | 테스트 9개. 「반드시 통과해야 하는 경우」를 못 박아 뒀습니다 |

**앱이 막지 않는 경우가 네 가지입니다.** 서버가 실수해도 전원이 잠기지 않게 한 것입니다.

1. 필드를 안 준다 → 통과 (지금 상태)
2. 설정 조회가 실패했다 → 통과. 서버 장애로 전원이 잠기는 쪽이 훨씬 나쁩니다
3. 앱이 자기 `versionCode` 를 못 읽었다 → 통과
4. **서버가 `0` 이나 음수를 줬다 → 통과.** 실수로 0 을 넣어 전원을 잠그는 사고를 막습니다

경계는 `<` 입니다. `minVersionCode` 와 **같은** 버전은 통과합니다.

## 한 가지 부탁 — 값을 환경변수로 빼 주세요

앱이 막아 주는 건 `0`·음수·누락까지입니다. **잘못된 큰 값은 앱이 막을 수 없습니다** —
`99999999` 을 넣으면 전원이 잠기고, 그 상태는 앱에서 되돌릴 수단이 없습니다(막힌 앱이
설정을 다시 받아도 같은 판정입니다). 그때 **코드 배포 없이 즉시 되돌릴 수 있어야** 합니다.

`moyeota.taxi.enabled` 와 같은 방식이면 충분합니다.

```yaml
moyeota:
  app:
    min-version-code: ${MIN_VERSION_CODE:}
    recommended-version-code: ${RECOMMENDED_VERSION_CODE:}
```

빈 값 = 기준 없음(JSON 에서 생략 또는 `null`) 으로 다뤄 주시면, 평소에는 아무것도 설정하지
않은 상태로 두고 필요한 날에만 값을 넣을 수 있습니다.

## 지금은 필요 없는 것

범위를 좁히려고 적습니다.

- **플랫폼 분기** — 안드로이드만 있습니다
- **안내 문구·스토어 링크** — 앱에 고정 문구와 Play 링크가 들어 있습니다
- **인증** — `/config` 는 지금처럼 인증 없이 두시면 됩니다. 앱의 게이트가 **로그인 화면보다
  앞**에 있어서, 로그인하지 않은 사용자도 막혀야 합니다

## 확인 부탁

- [ ] 두 필드가 **생략 가능**한지(값을 설정하지 않으면 응답에 안 나오거나 `null`)
- [ ] 환경변수로 바꿀 수 있는지
- [ ] 값을 넣은 뒤 캐시 없이 바로 반영되는지(앱은 시작할 때 한 번 조회합니다)
```

---

## 제출 뒤 남은 일 (이 줄 아래는 이슈 본문에 없다)

- **운영 `/config` 실측을 못 했다.** 이슈 본문에 「소스 기준이고 운영 응답을 직접 받아
  확인한 것은 아니다」라고 적어 두었다(`AppConfigResponse.java:3`). 네트워크가 되는
  자리에서 한 번 받아 보고, 다르면 이슈에 코멘트로 보탠다.
- 서버가 필드를 추가하면 고칠 자리 두 곳:
  `UpdateGate.kt:18` 「**지금 서버가 이 상태다**」 주석,
  `AppConfigDtos.kt:17` 「지금 서버는 주지 않는다」 주석,
  `UpdateGateTest.kt:23` 「2026-11-01 현재 서버 응답이 이 상태다」 주석.
- 서버가 값을 넣는 날 **되돌릴 수 있는지 먼저 확인**한다. 잘못된 큰 값은 앱이 막지 못하고,
  막힌 앱은 설정을 다시 받아도 같은 판정이라 서버에서 내리는 것만이 유일한 복구다.
