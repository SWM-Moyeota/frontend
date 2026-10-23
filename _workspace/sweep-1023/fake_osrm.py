"""로컬 QA용 가짜 OSRM. /table/v1/foot/... 요청에 그럴듯한 도보 시간을 돌려준다.

진짜 OSRM 이미지는 south-korea-latest.osm.pbf(약 1GB)를 받아 전처리해야 떠서 QA 한 번에는
과하다. 백엔드가 쓰는 건 /table 의 durations 행렬 하나뿐이라 그 모양만 맞추면 된다.
좌표 간 하버사인 거리 / 보행속도(1.3 m/s) 로 초를 만든다 — 값이 거리에 따라 달라져야
화면에서 "걸어서 N분"이 서로 다르게 나오는 걸 볼 수 있다.
"""
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
import json, math

WALK_MPS = 1.3

def haversine(a, b):
    (lng1, lat1), (lng2, lat2) = a, b
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lng2 - lng1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        # urlparse 는 path 의 ';' 를 params 로 잘라낸다 — OSRM 은 좌표 구분자로 쓰므로 직접 쪼갠다
        raw_path, _, raw_query = self.path.partition("?")
        if not raw_path.startswith("/table/v1/foot/"):
            self.send_error(404); return
        coords = [tuple(float(v) for v in c.split(",")) for c in raw_path[len("/table/v1/foot/"):].split(";")]
        q = parse_qs(raw_query)
        srcs = [int(i) for i in q.get("sources", ["0"])[0].split(";")]
        dsts = [int(i) for i in q.get("destinations", ["0"])[0].split(";")]
        durations = [[round(haversine(coords[s], coords[d]) / WALK_MPS, 1) for d in dsts] for s in srcs]
        body = json.dumps({"code": "Ok", "durations": durations}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass

if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 5000), Handler).serve_forever()
