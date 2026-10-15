# 모여타 서비스 소개 랜딩

서비스 소개 한 장짜리 정적 페이지의 **원본**이다.
`_workspace/legal-site/` 와 같은 이유로 저장소 안에 둔다 — 세션 임시 폴더에서 만들면
그 폴더가 초기화될 때 소스가 사라진다.

## 구성

```
index.html              페이지 전체 (자바스크립트 0)
assets/style.css        랜딩 전용 스타일. 법적 사이트의 토큰 블록을 그대로 이어받았다
assets/shots/*.webp     앱 스크린샷 9장 (전체 화면 8 + 요금 조각 1)
README.md               이 파일
_report_landing.md      구현 보고 — 명세와 다르게 간 곳, 검증 수치
```

**파일 11개 · 요청 11개 · 전송량 380 KB.** 더 늘리지 말 것.

## 제약 (어기면 페이지의 전제가 깨진다)

- **자바스크립트 0.** `<script>` 도 인라인 핸들러도 없다.
- **외부 요청 0.** 웹폰트·CDN·`@import`·외부 이미지가 한 건도 없다. 글꼴은 시스템 스택 하나다.
  (법적 문서로 가는 `https://legal.moyeota.p-e.kr/...` 링크는 **내비게이션**이지 서브리소스가 아니다.)
- **색면은 3종뿐** — `--bg` / `--surface-soft` / `--accent-soft`.
  `--accent-soft` 는 히어로 스테이지와 다운로드 패널 **두 곳**에만 쓴다.
- **채운 버튼은 페이지에 하나**(`어떻게 쓰는지 보기`). 그림자는 기기 목업에만 쓴다.

## 배포

**주소는 `https://www.moyeota.p-e.kr/` 다.** (2026-10-14 배포 완료)

| | 값 |
|---|---|
| 버킷 | `moyeota-landing` (ap-northeast-2) |
| 배포 ID | `E3JP82KLN42LB1` |
| CloudFront 도메인 | `dtxu7w08jl4u4.cloudfront.net` |
| DNS | `www` CNAME → 위 CloudFront 도메인 |

apex 인 `moyeota.p-e.kr` 은 **DNS 를 두지 않는다.** apex 에 CNAME 을 걸면 그 아래
`api`(백엔드)·`legal` 서브도메인 해석이 흔들리기 때문이다. 그래서 `index.html` 의
`canonical` 과 `og:url` 도 `www` 를 가리킨다 — 고칠 일이 있으면 **둘을 같이** 고칠 것.

```bash
aws s3 sync . s3://moyeota-landing/ --delete \
  --exclude "README.md" --exclude "_*" --exclude "*/_*" \
  --cache-control "public, max-age=300"

aws cloudfront create-invalidation --distribution-id E3JP82KLN42LB1 --paths '/*'
```

- 인증서는 **us-east-1** 에 있어야 한다 (CloudFront 는 버지니아 인증서만 읽는다).
  `*.moyeota.p-e.kr` 와일드카드 인증서가 이미 있으면 서브도메인은 그걸 쓸 수 있다.
- 하위 경로가 없는 1장짜리라 CloudFront Function(index 리라이트)은 필요 없다.
- `webp` 의 Content-Type 은 `image/webp` 로 나가야 한다. S3 sync 는 보통 알아서 붙이지만
  `--content-type` 이 어긋나면 이미지가 다운로드로 떨어진다. 올린 뒤 한 장 열어 확인할 것.

## 고칠 때 주의

### 1. 사실만 쓴다 — 최근에 앱에서 거짓 문구 19건을 걷어냈다

이 페이지에 **쓰면 안 되는 말** (코드로 확인한 금지 목록):
성별 조건 매칭 · 위치 공유 · 긴급 신고 · 요금 정산/결제 · 매너 점수 · 마일리지 ·
기사 배차 · 「인증된 사람만」.

**써도 되는 사실**: 같은 방향 사람과 택시비를 나눈다 · 휴대폰 인증을 거친 사람만 가입한다 ·
모인 사람끼리 채팅으로 만날 곳을 정한다 · 탑승 횟수가 보인다 · 만 14세 이상 가입.

- **「무료」를 단독으로 쓰지 말 것.** 「앱 이용은 무료, 택시 요금은 탑승자가 냄」을 **붙여서** 쓴다.
  떼어 놓으면 「택시가 무료」로 읽힌다. 히어로의 `.hero__fine` 두 문장을 쪼개지 말 것.
- 다운로드 버튼은 **`<span class="chip">`** 이다. `<a href="#">` 로 바꾸지 말 것 —
  없는 링크는 거짓 약속이다. 출시되면 그 자리의 주석대로 `a.btn.btn--primary` 로 교체하면 된다.

### 2. 토큰은 법적 사이트와 공유한다

`assets/style.css` 의 **1·3·4장(토큰 라이트/다크·리셋)과 6장(상단 바)·문서 카드·푸터**는
`_workspace/legal-site/assets/style.css` 에서 **한 글자도 바꾸지 않고 복사**한 것이다.
두 사이트가 한 서비스로 읽혀야 한다. **값을 고쳐야 할 이유가 생기면 양쪽을 같이 고친다.**

### 3. 스크린샷

- 전부 **개발 중 디버그 빌드**에서 찍은 것이다. 푸터에 그 고지가 들어 있다 — 지우지 말 것.
- 원본은 1080×2400 PNG, 내보내기는 **648×1440 webp**(×0.6, 정확히 9:20).
  기기 목업의 `aspect-ratio: 9/20` 와 맞아 `object-fit: cover` 의 자르기가 0 이다.
  **비율이 다른 이미지로 갈아 끼우면 화면이 잘린다.**
- 스크린샷에 `filter` / `opacity` / `mix-blend-mode` 를 걸지 말 것. 앱이 실제와 다르게 보인다.
  다크 모드에서도 기기 몸통(`--device-body`)은 어둡게 유지한다.
- 다시 찍으려면 촬영 보고서(`_shots.md`)의 시드 스크립트 순서를 따른다.
  **그 보고서와 디자인 명세는 세션 임시 폴더에만 있다 — 남겨야 한다면 이 폴더로 복사할 것.**

### 4. 기기 목업 치수를 손대지 말 것

`.device` 의 `border-radius: 13.6% / 6.12%` 와 `.device__screen` 의
`inset: 0.61% 1.35%` / `border-radius: 12.4% / 5.50%` 는 `aspect-ratio: 9/20` 에 맞춰
**모서리가 원형으로 유지되도록 유도한 값**이다 (유도 과정은 CSS 주석에 적혀 있다).
비율을 바꾸면 세 값을 다시 계산해야 한다.

### 5. 축소 모션에서 띠를 반드시 따로 끌 것

`@media (prefers-reduced-motion: reduce)` 안의

```css
.strip__row { animation: none !important; transform: none !important; }
```

이 두 줄이 없으면 `animation-duration: .01ms` 가 띠를 **즉시 끝 상태로 점프**시켜
두 번째(복제) 묶음만 보인다. 가장 틀리기 쉬운 곳이다.

### 6. 띠 이미지에 `loading="lazy"` 를 붙이지 말 것

복제(두 번째) 묶음 8칸은 **가로로만** 뷰포트 밖에 있다. 세로 스크롤로는 영영 들어오지 않고,
마퀴가 `translateX` 로 밀어 넣어도 크롬의 네이티브 지연 로딩이 따라오지 못한다
(실측: 40초에 8장 중 1장). 붙이면 **루프 두 번째 바퀴에 빈 기기 틀이 지나간다.**
URL 이 첫 묶음과 같아 빼도 요청 수는 늘지 않는다.
단계·가치 카드의 6장은 진짜로 아래에 있으므로 `lazy` 를 유지한다.

### 7. 띠의 이음매

칸 사이 여백을 `gap` 이 아니라 **각 칸의 `margin-inline-end`** 로 준다.
`gap` 이면 8칸 사이에 7칸만 생겨 `translateX(-50%)` 가 한 묶음과 어긋난다.
들쭉날쭉한 높이도 **주기 8**(`:nth-child(8n+2)` 등)로 맞춘다 — `2n`/`3n` 을 쓰면
주기가 6 이 되어 두 묶음의 스카이라인이 달라지고 한 바퀴마다 튄다.

## 검증 (고친 뒤 다시 돌릴 것)

```bash
cd _workspace/landing && python3 -m http.server 8777 --bind 127.0.0.1
```

- 320 / 390 / 430 / 600 / 768 / 900 / 1280 / 1440 / 1920px 에서 **가로 스크롤 0**
- 320px 에서 **h1 이 정확히 2줄**
- 라이트·다크 모두에서 기기 몸통이 어둡고, 다크에서 히어로 글로우가 흰색이 아닐 것
- `prefers-reduced-motion: reduce` 에서 **띠가 멈추고 첫 묶음이 보일 것**
- 전송량 400 KB · 요청 11개를 넘지 않을 것

수치는 `_report_landing.md` 에 적어 두었다.
