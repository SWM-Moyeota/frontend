import math, os, collections
from PIL import Image, ImageDraw

ROOT = "/Users/sungyoon/Desktop/moyeota/frontend"
SRC  = f"{ROOT}/_workspace/play-assets/00-logo-source-512.png"
RES  = f"{ROOT}/app/src/main/res"
DENS = {"mdpi":1, "hdpi":1.5, "xhdpi":2, "xxhdpi":3, "xxxhdpi":4}
FG_RADIUS_RATIO = 0.32     # 108dp 캔버스에서 외접원 지름 64% (72dp 마스크 원 66.7% 안쪽)

src = Image.open(SRC).convert("RGBA"); W,H = src.size; sp = src.load()

# ── 배경색 ────────────────────────────────────────────────────────
ring = [sp[x,y][:3] for y in range(H) for x in range(W) if x<6 or y<6 or x>=W-6 or y>=H-6]
BG = tuple(round(sum(c[i] for c in ring)/len(ring)) for i in range(3))
def araw(p): return max(0.0, max((BG[c]-p[c])/BG[c] for c in range(3)))

# ── 순수 마크색 2개 (k-means, 코어 픽셀 기준) ─────────────────────
core = [sp[x,y][:3] for y in range(H) for x in range(W) if araw(sp[x,y]) > 0.80]
M = [list(collections.Counter(core).most_common(1)[0][0]), None]
M[1] = list(max(core, key=lambda c: sum((c[i]-M[0][i])**2 for i in range(3))))
for _ in range(12):
    buckets = ([], [])
    for c in core:
        d0 = sum((c[i]-M[0][i])**2 for i in range(3))
        d1 = sum((c[i]-M[1][i])**2 for i in range(3))
        buckets[0 if d0 <= d1 else 1].append(c)
    for k in (0,1):
        if buckets[k]:
            M[k] = [sum(c[i] for c in buckets[k])/len(buckets[k]) for i in range(3)]
for k in (0,1):
    print(f"순수색 M{k+1} = #{round(M[k][0]):02X}{round(M[k][1]):02X}{round(M[k][2]):02X}  ({len(buckets[k])}px)")
print(f"배경 BG   = #{BG[0]:02X}{BG[1]:02X}{BG[2]:02X}")

# ── 배경에서 3px 이상 떨어진 픽셀은 "내부" — 무조건 불투명 ──
from PIL import ImageFilter
# 확실한 배경(araw<0.02)을 3px 팽창 → 그 밖은 전부 내부(불투명 고정)
bgbin = Image.new("L", (W,H), 0); bb = bgbin.load()
for y in range(H):
    for x in range(W):
        if araw(sp[x,y]) < 0.02: bb[x,y] = 255
bgdil = bgbin.filter(ImageFilter.MaxFilter(7)).load()

DEN = [sum((M[k][i]-BG[i])**2 for i in range(3)) for k in (0,1)]
mark = Image.new("RGBA", (W,H), (0,0,0,0)); mp = mark.load()
for y in range(H):
    for x in range(W):
        p = sp[x,y]
        if bb[x,y]: continue                      # 확실한 배경 → 투명
        if not bgdil[x,y]:                        # 배경에서 먼 내부 → 원색 그대로
            mp[x,y] = (p[0], p[1], p[2], 255); continue
        best_a, best_r = 0.0, 1e18                # 배경 경계 AA 띠 → 피복률 역산
        for k in (0,1):
            a = sum((p[i]-BG[i])*(M[k][i]-BG[i]) for i in range(3)) / DEN[k]
            ac = max(0.0, min(1.0, a))
            r = sum((p[i] - (ac*M[k][i] + (1-ac)*BG[i]))**2 for i in range(3))
            if r < best_r: best_r, best_a = r, ac
        if best_a <= 0.004: continue
        if best_a >= 0.999:
            mp[x,y] = (p[0], p[1], p[2], 255)
        else:
            m = tuple(max(0,min(255, round((p[i]-(1-best_a)*BG[i])/best_a))) for i in range(3))
            mp[x,y] = (*m, round(best_a*255))

# ── 검산 1: 배경색 위에 다시 합성하면 원본과 같아야 한다 ──────────
chk = Image.alpha_composite(Image.new("RGBA",(W,H),(*BG,255)), mark)
diffs = [max(abs(a-b) for a,b in zip(c1[:3],c2[:3])) for c1,c2 in zip(chk.getdata(), src.getdata())]
print(f"검산 재합성 오차: 최대 {max(diffs)}/255, 3 초과 픽셀 {sum(1 for d in diffs if d>3)}개")
# ── 검산 2: 반투명 잔재가 없어야 한다 ─────────────────────────────
op = sum(1 for y in range(H) for x in range(W) if mp[x,y][3] == 255)
semi = sum(1 for y in range(H) for x in range(W) if 32 < mp[x,y][3] < 255)
print(f"검산 알파: 완전불투명 {op}px / 반투명(AA) {semi}px")

cx, cy = W/2, H/2
maxr = max(math.hypot(x+.5-cx, y+.5-cy) for y in range(H) for x in range(W) if mp[x,y][3] > 32)
print(f"마크 외접 반지름 {maxr:.1f}px = 소스의 {200*maxr/W:.1f}%\n")

def webp(im, path): im.save(path, "WEBP", lossless=True, method=6, exact=True)

# ── 적응형 foreground ─────────────────────────────────────────────
for d, mul in DENS.items():
    S = round(108*mul); k = (FG_RADIUS_RATIO*S)/maxr; n = max(1, round(W*k))
    cv = Image.new("RGBA", (S,S), (0,0,0,0))
    cv.paste(mark.resize((n,n), Image.LANCZOS), ((S-n)//2, (S-n)//2))
    webp(cv, f"{RES}/mipmap-{d}/ic_launcher_foreground.webp")
# ── 레거시 사각(풀블리드) + 원형 ──────────────────────────────────
for d, mul in DENS.items():
    S = round(48*mul)
    base = src.resize((S,S), Image.LANCZOS)
    webp(base, f"{RES}/mipmap-{d}/ic_launcher.webp")
    rnd = base.copy(); SS = S*8
    m = Image.new("L", (SS,SS), 0); ImageDraw.Draw(m).ellipse((0,0,SS-1,SS-1), fill=255)
    rnd.putalpha(m.resize((S,S), Image.LANCZOS))
    webp(rnd, f"{RES}/mipmap-{d}/ic_launcher_round.webp")
# ── Play 스토어 아이콘 ────────────────────────────────────────────
play = src.copy(); play.putalpha(255)
for p in [f"{ROOT}/app/src/main/ic_launcher-playstore.png",
          f"{ROOT}/_workspace/play-assets/01-icon-512.png"]:
    play.save(p, "PNG", optimize=True)
print(f"colors.xml ic_launcher_background -> #{BG[0]:02X}{BG[1]:02X}{BG[2]:02X}")
