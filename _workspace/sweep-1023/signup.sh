#!/bin/bash
# 로컬 백엔드에 QA 계정 하나를 만들고 accessToken 을 /tmp/tok_<id> 에 남긴다.
# 인자: loginId nickname name phone gender
set -u
API=http://localhost:8080/api/v1
LOG=/tmp/moyeota-backend.log
ID=$1; NICK=$2; NAME=$3; PHONE=$4; GENDER=$5
# LoggingSmsSender 는 to=010-****-1234 로 마스킹해 찍는다 — 번호로 집어야 계정끼리 안 섞인다
MASK="010-****-${PHONE: -4}"

MARK=$(wc -l < "$LOG" | tr -d ' ')
printf '  sms/send  '
curl -s -X POST "$API/auth/sms/send" -H 'Content-Type: application/json' \
     -d "{\"phoneNumber\":\"$PHONE\"}" -o /tmp/sms_$ID.json -w '%{http_code}\n'

# 로그 플러시를 기다린다(최대 10초)
CODE=""
for _ in $(seq 1 20); do
  CODE=$(tail -n "+$MARK" "$LOG" | grep -F "to=$MASK" | grep -oE '\[[0-9]{6}\]' | tr -d '[]' | tail -1)
  [ -n "$CODE" ] && break
  sleep 0.5
done
echo "  code      ${CODE:-(못 찾음)}"
[ -z "$CODE" ] && exit 1

printf '  verify    '
curl -s -X POST "$API/auth/sms/verify" -H 'Content-Type: application/json' \
     -d "{\"phoneNumber\":\"$PHONE\",\"code\":\"$CODE\"}" -o /tmp/ver_$ID.json -w '%{http_code}\n'
TOKEN=$(python3 -c "import json;print(json.load(open('/tmp/ver_$ID.json')).get('verificationToken',''))" 2>/dev/null)
[ -z "$TOKEN" ] && { echo "  !! 토큰 없음: $(cat /tmp/ver_$ID.json)"; exit 1; }

printf '  register  '
curl -s -X POST "$API/auth/register" -H 'Content-Type: application/json' -d "{
  \"loginId\":\"$ID\",\"password\":\"Passw0rd!\",\"nickname\":\"$NICK\",\"name\":\"$NAME\",
  \"birthDate\":\"1998-03-12T00:00:00Z\",\"phoneNumber\":\"$PHONE\",
  \"email\":\"$ID@example.com\",\"gender\":\"$GENDER\",\"verificationToken\":\"$TOKEN\"}" \
  -o /tmp/reg_$ID.json -w '%{http_code}\n'
grep -q '"code"' /tmp/reg_$ID.json 2>/dev/null && cat /tmp/reg_$ID.json && echo

printf '  login     '
curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
     -d "{\"loginId\":\"$ID\",\"password\":\"Passw0rd!\"}" -o /tmp/login_$ID.json -w '%{http_code}\n'
python3 - "$ID" <<'PY'
import json, sys
i = sys.argv[1]
d = json.load(open(f"/tmp/login_{i}.json"))
t = d.get("accessToken") or d.get("data", {}).get("accessToken", "")
open(f"/tmp/tok_{i}", "w").write(t)
print("  access    " + (t[:28] + "..." if t else f"(없음) {d}"))
PY
