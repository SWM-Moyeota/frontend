#!/bin/bash
# uiautomator 기반 헬퍼 — 좌표를 하드코딩하지 않는다(에러 박스가 뜨면 버튼이 밀린다)
ADB=~/Library/Android/sdk/platform-tools/adb
dump() { $ADB shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $ADB shell cat /sdcard/u.xml 2>/dev/null; }
case "$1" in
  texts) dump | tr '>' '>\n' | grep -oE 'text="[^"]{1,}"' | sed 's/text="//;s/"$//' | sort -u ;;
  has)   dump | tr '>' '>\n' | grep -qE "text=\"$2\"" && echo YES || echo NO ;;
  tap)
    B=$(dump | tr '>' '>\n' | grep -oE "text=\"$2\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
    [ -z "$B" ] && { echo "못 찾음: $2"; exit 1; }
    read X Y <<< "$(python3 -c "
import re,sys
m=re.findall(r'\[(\d+),(\d+)\]','$B')
print((int(m[0][0])+int(m[1][0]))//2, (int(m[0][1])+int(m[1][1]))//2)")"
    $ADB shell input tap $X $Y; echo "tap '$2' ($X,$Y)" ;;
  typeinto)
    B=$(dump | tr '>' '>\n' | grep -oE "text=\"$2\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
    [ -z "$B" ] && { echo "못 찾음: $2"; exit 1; }
    read X Y <<< "$(python3 -c "
import re
m=re.findall(r'\[(\d+),(\d+)\]','$B')
print((int(m[0][0])+int(m[1][0]))//2, (int(m[0][1])+int(m[1][1]))//2)")"
    $ADB shell input tap $X $Y; sleep 0.5; $ADB shell input text "$3"; echo "type '$3' into '$2'" ;;
  shot) $ADB exec-out screencap -p > "$2"; echo "saved $2" ;;
esac
