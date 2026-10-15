# 공개 웹 자산 실사 — 2026-10-14

읽기 전용 점검. **배포·무효화·코드 변경 없음.** AWS 자격증명 있음
(`arn:aws:iam::116662357283:user/sungyoon`) — `s3 ls` / `cloudfront get-*` 만 사용.

---

## 1. URL 별 상태코드

### 앱이 여는 주소 (`presentation/src/main/kotlin/com/moyeota/presentation/core/SupportLinks.kt`)

| 상수 | URL | 코드 | 리다이렉트 | 본문 |
|---|---|---|---|---|
| `TERMS_URL` | `https://legal.moyeota.p-e.kr/terms/` | **200** | 없음 | 36,988 B · 본문 11,733자 렌더 |
| `PRIVACY_URL` | `https://legal.moyeota.p-e.kr/privacy/` | **200** | 없음 | 50,025 B · 본문 13,565자 렌더 |
| `ACCOUNT_DELETION_URL` | `https://legal.moyeota.p-e.kr/account-deletion/` | **200** | 없음 | 26,375 B · 본문 7,070자 렌더 |
| `CONTACT_EMAIL` | `mailto:codingforest17@gmail.com` | — | — | `ACTION_SENDTO`, 앱 없으면 토스트 폴백 |

**리다이렉트 루프 0 · 404 0 · 출시 차단 사유 없음.**

### 끝 슬래시 유무 — 둘 다 확인

CloudFront Function `moyeota-legal-index-rewrite` (viewer-request) 가 `/` 로 끝나면
`index.html` 을 붙이고, 점이 없으면 `/index.html` 을 붙인다. **리다이렉트가 아니라 내부
재작성**이라 슬래시 없는 주소도 그 자리에서 200 이다.

| URL | 코드 | 비고 |
|---|---|---|
| `/terms/` · `/terms` | 200 · 200 | 바이트 동일 (36,988) |
| `/privacy/` · `/privacy` | 200 · 200 | 바이트 동일 (50,025) |
| `/account-deletion/` · `/account-deletion` | 200 · 200 | 바이트 동일 (26,375) |
| `/privacy/index.html` | 200 | 직접 접근도 열림 |
| `/` | 200 | 목차 (4,136) |
| `/assets/style.css` | 200 | `text/css`, 50,293 |
| `/nope` · `/nope/` | 403 · 403 | 없는 경로 — S3 OAC 기본값 |
| `http://legal…/privacy/` | 301 → `https://…` | `redirect-to-https` |

> 슬래시 없는 형태에서도 **깨지는 상대경로가 없다.** 네 문서의 링크·CSS 가 전부
> 루트 절대경로(`/privacy/`, `/assets/style.css`)라 `/privacy` 로 열어도 동일하게 뜬다.
> 각 문서에 `rel="canonical"` 이 슬래시 있는 형태로 박혀 있어 중복 색인도 정리된다.

### 법적 문서 안의 외부 링크 (법정 고지 기관)

| URL | 코드 |
|---|---|
| `https://privacy.kisa.or.kr` | 200 |
| `https://ecrm.police.go.kr` | 302 → `/minwon/main` (최종 200) |
| `https://www.kopico.go.kr` | 302 → `https://kopico.go.kr/` (최종 200) |

> `kopico` 는 기본 curl UA 에 400 을 주고 브라우저 UA 에는 302→200 을 준다. UA 필터링일 뿐
> 실제 사용자에겐 정상.

### 랜딩

| URL | 코드 | 비고 |
|---|---|---|
| `https://www.moyeota.p-e.kr/` | **200** | 19,834 B — 소스와 바이트 동일 |
| `http://www.moyeota.p-e.kr/` | 301 → https | |
| `https://www.moyeota.p-e.kr/assets/style.css` | 200 | `text/css` |
| `https://www.moyeota.p-e.kr/assets/shots/14-home.webp` | 200 | **`image/webp`** — README 가 걱정한 Content-Type 정상 |
| `https://moyeota.p-e.kr/` (apex) | **해석 실패** | NXDOMAIN — 아래 §2 |

---

## 2. DNS 실제 상태

권한 네임서버: `1.ns.dnsze.com` / `2.ns.dnsze.com` (p-e.kr 무료 DNS).

| 이름 | 타입 | 실제 값 | 상태 |
|---|---|---|---|
| `legal.moyeota.p-e.kr` | CNAME | `duue1p24l1tau.cloudfront.net` | ✅ |
| `www.moyeota.p-e.kr` | CNAME | `dtxu7w08jl4u4.cloudfront.net` | ✅ **이미 넣었다** |
| `api.moyeota.p-e.kr` | CNAME | `mo-1919120051.ap-northeast-2.elb.amazonaws.com` | ✅ |
| `moyeota.p-e.kr` (apex) | — | **NXDOMAIN** (8.8.8.8 · 권한 NS 모두) | ❌ |

> **결론: `www` CNAME 은 이미 들어가 있고 랜딩은 살아 있다.** 작업이 남은 건 apex 뿐이다.

TLS: 양쪽 다 `CN=*.moyeota.p-e.kr`, SAN `*.moyeota.p-e.kr` + `moyeota.p-e.kr`,
2026-10-13 ~ 2027-04-28. **apex 도 인증서 범위 안에 이미 들어 있다.**

CloudFront 배포 현황 (읽기):

| ID | 도메인 | Alias | Origin | 상태 |
|---|---|---|---|---|
| `EFF393Z7SXDBF` | `duue1p24l1tau.cloudfront.net` | `legal.moyeota.p-e.kr` | `moyeota-legal-site.s3` | Deployed |
| `E3JP82KLN42LB1` | `dtxu7w08jl4u4.cloudfront.net` | `www.moyeota.p-e.kr` | `moyeota-landing.s3` | Deployed |
| `E39V1OT94IP5CL` | — | `api.moyeota.p-e.kr` | 백엔드 ELB | Deployed |
| `E1R7GDPJDC601E` | `d3ddnm4f2lvu0g…` | (없음) | `moyeota-media.s3` | Deployed |

둘 다 `GeoRestriction: none` · `redirect-to-https` · `PriceClass_200`.
**지역 차단 없음 → 플레이 심사자가 어디서 열어도 뜬다.**

### ⚠ apex(`moyeota.p-e.kr`) 가 비어 있는데 랜딩이 거기를 가리킨다

`_workspace/landing/index.html` 13·19행 (배포본도 동일):

```html
<link rel="canonical" href="https://moyeota.p-e.kr/">
<meta property="og:url"  content="https://moyeota.p-e.kr/">
```

이 주소는 **NXDOMAIN 이다.** 사용자가 보는 화면은 멀쩡하지만
① 검색엔진이 존재하지 않는 주소를 정본으로 받고 ② 카톡·슬랙에 링크를 붙이면 미리보기의
`og:url` 이 죽은 주소로 찍힌다. **출시 차단은 아니지만 공유하기 전에 고쳐야 한다.**

해결은 둘 중 하나 — **A 를 권한다.**

**A. canonical/og:url 을 `www` 로 맞춘다 (권장, 소스 2줄)**
`_workspace/landing/index.html` 의 두 값을 `https://www.moyeota.p-e.kr/` 로 바꾸고
(11~12행 경고 주석도 같이 정리) 재배포. DNS 작업 없음.

**B. apex 를 살린다 (DNS + CloudFront 양쪽 필요)**
1. DNS 패널에 레코드 추가 —

   | 항목 | 값 |
   |---|---|
   | 이름(Host) | `moyeota` *(패널 기준이 `p-e.kr` 일 때. 전체 이름은 `moyeota.p-e.kr`)* |
   | 타입 | `CNAME` |
   | 값(Target) | `dtxu7w08jl4u4.cloudfront.net` |
   | TTL | `300` |

2. 배포 `E3JP82KLN42LB1` 의 Alias 에 `moyeota.p-e.kr` 를 **추가**해야 한다.
   지금은 `www.moyeota.p-e.kr` 하나뿐이라, DNS 만 넣으면 CloudFront 가
   `Bad request (403 — not configured for this domain)` 을 돌려준다. 인증서는 이미 apex 를
   덮고 있으므로 재발급은 불필요.

> **B 의 위험:** `moyeota.p-e.kr` 에 CNAME 을 걸면 그 아래 `legal.` · `www.` · `api.` 가
> 전부 하위 이름이 된다. RFC 1034 상 CNAME 이 있는 이름에는 다른 데이터가 올 수 없어서,
> 공급자에 따라 **생성을 거부하거나 하위 레코드 해석이 흔들릴 수 있다.** `api` 가 백엔드라
> 여기서 사고가 나면 앱 전체가 멈춘다. 그래서 A 를 권한다.

---

## 3. 전화번호 노출 여부

**결과: 010-2365-2548 은 어디에도 없다. 0건.**

검사한 범위 (전수):

| 대상 | 파일 | 결과 |
|---|---|---|
| 배포본 — 법적 사이트 | `/`, `/privacy/`, `/terms/`, `/account-deletion/`, `/assets/style.css` (S3 객체 5개 = 전부) | 0건 |
| 배포본 — 랜딩 | `https://www.moyeota.p-e.kr/` 본문 | 0건 |
| 저장소 소스 — 법적 | `_workspace/legal-site/` 전체 재귀 | 0건 |
| 저장소 소스 — 랜딩 | `_workspace/landing/` 전체 재귀 | 0건 |

검사 패턴: `010[-. ]?2365[-. ]?2548` · `2365[-. ]?2548` · 일반형
`010[-. ]?[0-9]{3,4}[-. ]?[0-9]{4}` · `tel:` 링크.

공개 연락처로 실제 실려 있는 것은 **이메일뿐**이다:

| 페이지 | `codingforest17@gmail.com` 등장 | 그 밖의 이메일 |
|---|---|---|
| 법적 목차 | 2 | 없음 |
| 개인정보 처리방침 | 6 | 없음 |
| 이용약관 | 6 | 없음 |
| 계정 삭제 안내 | 8 | 없음 |
| 랜딩 | 4 | 없음 |

> **오탐 주의 — 지우면 안 되는 번호가 셋 있다.** 처리방침 제15조의
> `118`(개인정보침해 신고센터) · `1833-6972`(개인정보 분쟁조정위원회) · `182`(경찰청
> 사이버수사국). 개인정보보호법상 **고지 의무가 있는 공공기관 번호**이고 운영자 개인 번호가
> 아니다. 그대로 두는 것이 맞다.

개인정보 보호책임자 표기도 `황성윤 · codingforest17@gmail.com` 뿐 — 번호 없음.

---

## 4. 소스 ↔ 배포본 일치 여부

**두 사이트 모두 완전 일치. 어긋난 파일 0건. 한쪽만 오래된 상태 아님.**

### 법적 사이트 (`moyeota-legal-site` / `EFF393Z7SXDBF`)

HTTPS 로 받은 본문 md5 = 저장소 소스 md5 = S3 ETag, 5개 전부:

| 경로 | md5 | 판정 |
|---|---|---|
| `index.html` | `ee30e6a7f47f5bcfd20728e444bb186b` | 동일 |
| `privacy/index.html` | `4a6afe436247350a0b6533cdf9194fe8` | 동일 |
| `terms/index.html` | `13410030efffc3cb019da05df7ed6fe6` | 동일 |
| `account-deletion/index.html` | `9d1df44692b97f77bf25c6c3173115d2` | 동일 |
| `assets/style.css` | `c0aa687ce1afaef26d33257e17e121ed` | 동일 |

버킷 객체는 정확히 이 5개뿐 — **과거 배포의 잔재나 유출된 임시 파일 없음.**
업로드 시각 전부 `2026-10-14 15:32`. `README.md` · `_check.py` · `_changes.md` 는
`--exclude` 규칙대로 올라가지 않았다. ✅

구조 검사도 통과 (소스에 대해 읽기 전용 실행):
```
python3 _check.py .   →  실패 파일: 0   (4개 문서, 태그·id·목차·앵커 전부 일치)
```

### 랜딩 (`moyeota-landing` / `E3JP82KLN42LB1`)

11개 객체 전부 ETag = 로컬 md5. 한쪽에만 있는 파일 0건.
`index.html` · `assets/style.css` · `assets/shots/*.webp` 9장.
업로드 시각 `2026-10-14 14:47`. README/`_report_landing.md` 는 제외돼 올라가지 않았다. ✅

---

## 5. 플레이 스토어 등록용 URL

| 콘솔 항목 | 값 | 확인 |
|---|---|---|
| **개인정보처리방침** (앱 콘텐츠 → 개인정보처리방침) | `https://legal.moyeota.p-e.kr/privacy/` | 200 · 로그인 불필요 · 지역차단 없음. `SupportLinks.PRIVACY_URL` 과 **동일 페이지** ✅ |
| **계정 삭제 요청 URL** (데이터 안전 → 데이터 삭제) | `https://legal.moyeota.p-e.kr/account-deletion/` | 200 · **앱 미설치자도 열람 가능** (페이지 본문이 그 경로를 명시) ✅ |
| **지원 이메일** (스토어 설정 → 연락처) | `codingforest17@gmail.com` | 앱·웹 전 자산에서 동일 ✅ |
| **웹사이트** (선택) | `https://www.moyeota.p-e.kr/` | 200 ✅ — apex 아님에 주의 |
| 이용약관 (스토어 필수 아님, 앱 내 링크) | `https://legal.moyeota.p-e.kr/terms/` | 200 ✅ |

교차 확인:
- 앱 `applicationId = "com.moyeota"` ↔ 계정 삭제 안내 본문의 `com.moyeota` **일치** ✅
- 처리방침 제13조 「만 14세 미만은 가입할 수 없습니다 … 생년월일을 입력받아 만 14세 미만이면
  다음 단계로 진행할 수 없습니다」가 **배포본에 실제로 렌더됨** ✅
  (`SupportLinks.kt` 가 요구하는 고지 ↔ `AuthPolicy.MIN_AGE` 동작 일치)
- 앱 소스 전체(`*.kt`/`*.xml`)의 운영 외부 URL 은 위 셋 + mailto 가 전부.
  `example.com` · `cdn.moyeota.com` 등은 **테스트/프리뷰 코드에만** 존재 — 사용자에게 안 보임 ✅
- 링크 여는 코드(`presentation/.../core/ExternalIntents.kt`)는 `startActivitySafely` 로
  `ActivityNotFoundException` 을 잡아 토스트로 바꾼다. `<queries>` 미선언이지만
  `startActivity` 는 패키지 가시성 제한을 받지 않으므로 **문제 없음** ✅

---

## 6. 사용자가 직접 해야 할 일

출시를 막는 항목은 **없다.** 아래는 공유 전에 정리하면 좋은 것들.

| # | 할 일 | 급한 정도 | 누가 |
|---|---|---|---|
| 1 | 랜딩 `canonical`/`og:url` 을 `https://www.moyeota.p-e.kr/` 로 고치고 재배포 (§2-A) — 지금은 NXDOMAIN 을 가리킨다 | **공유 전 필수** | 개발 (소스 2줄 + `aws s3 sync`) |
| 2 | (1 대신 apex 를 쓰겠다면) DNS 에 `moyeota` CNAME → `dtxu7w08jl4u4.cloudfront.net` 추가 **＋** 배포 `E3JP82KLN42LB1` Alias 에 `moyeota.p-e.kr` 추가. 하위 도메인(`api` 포함) 영향 위험 있음 | 선택 | 사용자(DNS 패널) + 개발(CloudFront) |
| 3 | 플레이 콘솔에 §5 네 값 입력 | 출시 시 | 사용자 |
| 4 | 랜딩 `og:image` 가 없다 — 링크 공유 시 썸네일이 비어 보인다 | 낮음 | 개발 |
| 5 | `_workspace/landing/README.md` 의 「아직 주소가 정해지지 않았다 / moyeota.p-e.kr 은 DNS 가 없다」는 **이미 틀렸다.** 실제 배포처(`moyeota-landing`, `E3JP82KLN42LB1`, `www.moyeota.p-e.kr`)를 적어 둘 것 | 낮음 | 개발 |
| 6 | 랜딩 배포에 법적 사이트용 Function `moyeota-legal-index-rewrite` 가 **공유로 붙어 있다.** 지금은 무해(1장짜리 + DefaultRootObject)하지만 한쪽 사정으로 함수를 고치면 다른 쪽이 같이 바뀐다 — 알고 있을 것 | 낮음 | 개발 |

### 참고로 확인했으나 문제 아니었던 것

- `robots.txt` 가 양쪽 403 — 구글은 429 외 4xx 를 「robots.txt 없음 = 전체 허용」으로 본다.
- 랜딩의 `href="스토어주소"` — **HTML 주석 안**이다(`<!-- 출시 후: … 로 교체 -->`).
  실제 마크업은 `<span class="chip">Google Play · 준비 중</span>` 이라 죽은 링크가 아니다.
- 슬래시 없는 `/privacy` 가 리다이렉트 없이 200 (중복 주소) — 각 문서의 `canonical` 이
  슬래시 있는 형태를 가리켜 색인은 하나로 모인다.
