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
- 아직 사실이 아닌 한 줄: **개인정보 처리방침의 성별 「선택」 표기.** 서버와 앱이
  아직 성별을 필수로 받는다(Backend #227). 둘 다 바뀐 뒤에야 참이 된다.
- 약관과 처리방침이 택시 기능을 두고 서로 다르게 서술한다 — 공개 전에 한쪽으로 맞춰야 한다.
