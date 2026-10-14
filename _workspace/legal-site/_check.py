#!/usr/bin/env python3
"""법적 고지 사이트 구조 검사.

1. 태그 균형 (HTMLParser 로 스택 검사)
2. id 중복 0
3. 목차 <-> 본문 id 양방향 일치
4. 문서 내 모든 #앵커가 실제 id 를 가리키는지 (앵커 깨짐 0)
5. 문서 간 상대 링크(/privacy/ 등)가 실제 파일을 가리키는지
"""
import sys
import re
from html.parser import HTMLParser
from pathlib import Path

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"}


class Checker(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.errors = []
        self.ids = []
        self.anchors = []        # (href, line)
        self.toc_anchors = []    # 목차 nav 안의 href
        self.headings = []       # (tag, id, text)
        self._in_toc = 0
        self._in_heading = None
        self._heading_text = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        line = self.getpos()[0]
        if "id" in a:
            self.ids.append((a["id"], line))
        if tag == "a" and "href" in a:
            href = a["href"]
            self.anchors.append((href, line))
            if self._in_toc and href.startswith("#"):
                self.toc_anchors.append(href[1:])
        if tag == "nav" and a.get("class") == "toc-landmark":
            self._in_toc += 1
        elif self._in_toc and tag == "nav":
            self._in_toc += 1
        if tag in ("h1", "h2", "h3"):
            self._in_heading = (tag, a.get("id"), line)
            self._heading_text = []
        if tag not in VOID:
            self.stack.append((tag, line))

    def handle_endtag(self, tag):
        line = self.getpos()[0]
        if tag in VOID:
            return
        if self._in_heading and tag == self._in_heading[0]:
            t, i, l = self._in_heading
            self.headings.append((t, i, "".join(self._heading_text).strip(), l))
            self._in_heading = None
        if tag == "nav" and self._in_toc:
            self._in_toc -= 1
        if not self.stack:
            self.errors.append(f"L{line}: </{tag}> 인데 열린 태그가 없다")
            return
        top, topline = self.stack.pop()
        if top != tag:
            self.errors.append(
                f"L{line}: </{tag}> 가 <{top}>(L{topline}) 를 닫으려 한다 — 태그 불균형")

    def handle_data(self, data):
        if self._in_heading:
            self._heading_text.append(data)


def check(path: Path, root: Path):
    src = path.read_text(encoding="utf-8")
    c = Checker()
    c.feed(src)
    c.close()
    problems = list(c.errors)
    if c.stack:
        problems.append("닫히지 않은 태그: " +
                        ", ".join(f"<{t}>(L{l})" for t, l in c.stack))

    ids = [i for i, _ in c.ids]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        problems.append(f"중복 id: {sorted(dup)}")
    idset = set(ids)

    # 앵커 깨짐
    for href, line in c.anchors:
        if href.startswith("#"):
            if href[1:] not in idset:
                problems.append(f"L{line}: 앵커 #{href[1:]} 에 해당하는 id 가 없다")
        elif href.startswith("/"):
            path_part, _, frag = href.partition("#")
            target = root / path_part.lstrip("/")
            if path_part.endswith("/"):
                target = target / "index.html"
            if not target.exists():
                problems.append(f"L{line}: 링크 {href} → {target} 파일이 없다")
            elif frag:
                other = target.read_text(encoding="utf-8")
                if not re.search(r'id\s*=\s*["\']' + re.escape(frag) + r'["\']', other):
                    problems.append(
                        f"L{line}: 문서 간 앵커 {href} — {target.name} 에 id={frag} 가 없다")

    # 목차 <-> 본문 양방향
    body_ids = [i for t, i, _, _ in c.headings if t == "h2" and i]
    toc = c.toc_anchors
    # 목차에 있으나 본문 h2/h3 id 로 없는 것
    heading_ids = {i for _, i, _, _ in c.headings if i}
    missing_in_body = [a for a in toc if a not in heading_ids]
    if missing_in_body:
        problems.append(f"목차에 있으나 본문 제목 id 에 없다: {missing_in_body}")
    missing_in_toc = [i for i in body_ids if i not in toc]
    if missing_in_toc:
        problems.append(f"본문 h2 id 인데 목차에 없다: {missing_in_toc}")

    # 조문 번호 <-> 목차 라벨 대조 (제N조 숫자가 id 순서와 맞는지)
    for t, i, text, line in c.headings:
        if i and i.startswith("article-"):
            n = i.split("-")[1]
            m = re.match(r"제(\d+)조", text)
            if m and m.group(1) != n:
                problems.append(
                    f"L{line}: id={i} 인데 제목이 「{text}」 — 번호 불일치")

    return problems, c


def main():
    root = Path(sys.argv[1])
    files = sorted(root.rglob("*.html"))
    bad = 0
    for f in files:
        problems, c = check(f, root)
        rel = f.relative_to(root)
        if problems:
            bad += 1
            print(f"[FAIL] {rel}")
            for p in problems:
                print(f"       - {p}")
        else:
            print(f"[ OK ] {rel}  (id {len(c.ids)}개, 앵커 {len(c.anchors)}개, "
                  f"목차 {len(c.toc_anchors)}항, 제목 {len(c.headings)}개)")
    print()
    print("실패 파일:", bad)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
