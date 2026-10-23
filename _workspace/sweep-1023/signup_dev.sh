#!/bin/bash
# dev 프로파일용 가입 — 인증번호가 고정값(SMS_FIXED_CODE, 기본 000000)이라 로그를 긁지 않는다.
set -u
API=http://localhost:8080/api/v1
ID=$1; NICK=$2; NAME=$3; PHONE=$4; GENDER=$5; CODE=${6:-000000}
curl -s -X POST "$API/auth/sms/send" -H 'Content-Type: application/json' -d "{\"phoneNumber\":\"$PHONE\"}" -o /dev/null
TOKEN=$(curl -s -X POST "$API/auth/sms/verify" -H 'Content-Type: application/json' \
  -d "{\"phoneNumber\":\"$PHONE\",\"code\":\"$CODE\"}" \
  | python3 -c 'import json,sys;print(json.load(sys.stdin).get("verificationToken",""))' 2>/dev/null)
[ -z "$TOKEN" ] && { echo "  !! 인증 실패"; exit 1; }
curl -s -X POST "$API/auth/register" -H 'Content-Type: application/json' -d "{
  \"loginId\":\"$ID\",\"password\":\"Passw0rd!\",\"nickname\":\"$NICK\",\"name\":\"$NAME\",
  \"birthDate\":\"1998-03-12T00:00:00Z\",\"phoneNumber\":\"$PHONE\",
  \"email\":\"$ID@example.com\",\"gender\":\"$GENDER\",\"verificationToken\":\"$TOKEN\"}" \
  -o /dev/null -w "  register %{http_code}  "
curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"loginId\":\"$ID\",\"password\":\"Passw0rd!\"}" \
  | python3 -c "import json,sys;t=json.load(sys.stdin).get('accessToken','');open('/tmp/tok_$ID','w').write(t);print('login', 'OK' if t else 'FAIL')"
