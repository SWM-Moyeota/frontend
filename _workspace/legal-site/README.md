# 법적 고지 사이트 소스

`https://legal.moyeota.p-e.kr/` 로 배포되는 정적 사이트의 **원본**이다.

## 왜 저장소 안에 있나

처음에는 세션 임시 폴더에서 만들었는데 **그 폴더가 초기화되면서 소스가 사라졌다.**
S3 배포본에서 복구해 여기에 넣는다. 이 주소는 **Google Play 콘솔에 등록되는 주소**라
없어지면 안 되고, 고칠 때 근거가 될 원본이 필요하다.

## 구성

```
index.html                  목차
privacy/index.html          개인정보 처리방침
account-deletion/index.html 계정 및 데이터 삭제 안내
terms/index.html            이용약관
assets/style.css            공통 스타일 (자바스크립트·외부 요청 0)
```

## 배포

```bash
aws s3 sync . s3://moyeota-legal-site/ --delete \
  --exclude "README.md" --exclude "_*" --exclude "*/_*" \
  --cache-control "public, max-age=300"

aws cloudfront create-invalidation --distribution-id EFF393Z7SXDBF --paths '/*'
```

- 버킷: `moyeota-legal-site` (ap-northeast-2, 비공개. CloudFront 만 읽는다)
- CloudFront: `EFF393Z7SXDBF` → `duue1p24l1tau.cloudfront.net`
- 인증서: `*.moyeota.p-e.kr` (us-east-1. **CloudFront 는 버지니아 인증서만 읽는다**)
- DNS: `legal` CNAME → `duue1p24l1tau.cloudfront.net`
- `/privacy/` 같은 하위 경로는 CloudFront Function `moyeota-legal-index-rewrite` 가
  `index.html` 을 붙여 준다. S3 는 디렉토리 색인을 모른다.

## 고칠 때 주의

- **경로를 바꾸지 마라.** Play 콘솔에 등록된 주소다.
- **본문은 코드에서 확인한 사실만 적는다.** 실제 동작과 어긋나면 허위 고지가 된다.
  어떤 개인정보를 어디에 보내는지 전수 조사한 기록이 작업 당시 있었고,
  그 결론이 본문에 반영돼 있다.
- 서버 동작이 바뀌면 **이 문서도 같이 고쳐야 한다.** 특히 보관 기간, 제3자 전송,
  탈퇴 시 남는 데이터.
- **아직 사실이 아닌 한 줄: 없다.** (2026-10-14 교정 — 무엇을 왜 고쳤는지는 `_changes.md`)
  - 성별 「선택」 표기는 **「필수」로 되돌렸다.** 서버 `UserRegisterRequest` 의
    `@NotNull Gender gender` 와 앱 `SignupDraft.toNewUser()` 가 아직 성별을 요구한다.
  - 약관과 처리방침의 택시 기능 서술은 **처리방침을 조건부로 바꿔** 맞췄다.

## 서버 동작이 바뀌면 고쳐야 할 자리

| 서버/앱이 이렇게 바뀌면 | 문서의 이 자리를 고친다 |
|---|---|
| **성별이 선택으로 바뀐다** — 서버 `UserRegisterRequest` 의 `@NotNull` 이 빠지고(Backend #227), 앱 `SignupDraft.toNewUser()` 가 성별 없이도 `NewUser` 를 만들게 된다. **둘 다** 바뀐 뒤에만 참이다 | ① `privacy` 제1조 머리 「아래 항목은 **모두 필수**입니다」 → 성별만 선택이라고 되돌린다 ② `privacy` 제1조 표1 「성별」 행 제목에 「(선택)」을 붙인다. 통계 목적 서술은 그대로 둔다(매칭에 안 쓰는 것은 확인됐다 — 백엔드 `matching` 패키지에 `gender` 0건) ③ `terms` 제7조 1항 2호의 입력 항목 나열은 그대로 둔다(선택도 입력란은 있다) |
| **택시 호출 기능을 켠다** — `TAXI_ENABLED=true`(운영은 `application-prod.yaml` 기본 false) | ① `privacy` 머리말의 `notice` 를 지운다 ② 「택시 호출 기능이 켜진 경우에만 해당합니다. 현재는 제공되지 않습니다」·「(택시 호출 기능이 켜진 경우에만)」 단서를 전부 지운다(`grep -c '택시 호출 기능이 켜진 경우'` → privacy 18 · account-deletion 3) ③ 제14조 머리의 `notice` 를 지운다 ④ `terms` 제4조 5항·제15조 3항을 제3조 절차에 따라 **먼저** 개정한다 |
| **앱에 메시지 삭제 UI 가 붙는다** — `presentation` 이 `ChatRepository.deleteMessage` 를 부르게 된다(지금은 호출처 0건) | ① `terms` 제13조 3항 ② `privacy` 제4조 3항 ③ `privacy` 제11조 1항에 항목을 되살리고 2항의 이메일 항목을 뺀다 ④ `privacy` 제11조 5항 ⑤ `account-deletion` 「계정은 그대로 두고 일부만」의 채팅 메시지 항목 |
| **앱에 즐겨찾기 삭제 UI 가 붙는다** — 서버 `DELETE /users/me/favorite-places/{placeName}`(Backend #238, 아직 OPEN)이 배포되고 앱이 그걸 부르게 된다. **둘 다** 된 뒤에만 참이다. 참고로 지금은 **등록 자체가 500** 이라(Backend #231, `FavoritePlaceId` 에 무인자 생성자 없음) 지울 대상도 안 생긴다 | ① `privacy` 제11조 2항의 「자주 가는 곳을 하나씩 삭제해 달라는 요구」를 **1항으로 옮긴다** — 2항은 「앱에 해당 기능이 아직 없어서」가 이유라 그대로 두면 허위 고지가 된다 ② `privacy` 제3조 표의 「자주 가는 곳을 저장할 때」 행에서 「그 전에 지우고 싶으면 제15조의 이메일로 요청해 주세요」를 앱에서 직접 지울 수 있다는 서술로 바꾼다 ③ `account-deletion` 의 「계정은 그대로 두고 일부만」 절 |
| **앱에 위치 공유가 돌아온다** — `ChatApi` 에 `…/location/sharing` 이 생긴다 | `privacy` 제3조 4항과 제4조 4항(마지막 단서 문장) |
| **앱에 채팅 신고 UI 가 붙는다** — 서버 `POST /api/v1/chat-rooms/{id}/reports` 와 `chat_report` 테이블은 **이미 있고 앱에만 없다** | `privacy` 제1조 표2·제6조 표에 「채팅 신고 기록」 줄을 넣고, `terms` 제15조 1항의 「메일로 알려 주세요」를 고친다 |
| **매칭이 반경·방향을 보기 시작한다** — 지금은 `GET /matching/rooms?swLat…` 가 출발지의 지도 뷰포트만 거른다. 반경은 서버 고정값 500m 이고 매칭에 쓰이지 않는다(Backend `a668aee`) | ① `terms` 제4조 1항 1호 ② `terms` 제11조 1항 ③ `terms` 제2조에 「탐색 반경」 정의를 되살린다(2026-10-14 에 지웠다) ④ `privacy` 제2조 2호 |
| **보관 기간을 정한다** | `privacy` 제6조 표와 그 아래 `notice`, `account-deletion` 의 「기간을 정해 두지 않았습니다」 전부 |

## 구조 검사

본문을 고쳤으면 배포 전에 파서로 확인한다 — 태그 균형, id 중복, 목차 ↔ 본문 `id`
양방향 일치, 문서 내 `#앵커`와 문서 간 링크의 조각(`/terms/#article-4`)까지 실재,
`id="article-N"` 과 제목 「제N조」의 번호 일치.

```bash
python3 _check.py .     # 네 문서 전부. 실패 0 이어야 배포한다
```

`_check.py` 도 `_` 로 시작하므로 배포 대상이 아니다. 의존성은 표준 라이브러리뿐이다.
고치지 않은 부분이 그대로인지는 배포본과 `diff` 로 따로 확인한다:

```bash
aws s3 sync s3://moyeota-legal-site/ /tmp/legal-live/ --delete   # 현재 배포본
diff -ru /tmp/legal-live/ . -x '_*' -x 'README.md'
```
