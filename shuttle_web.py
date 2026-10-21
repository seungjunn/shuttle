#!/usr/bin/env python3
"""꿈에그린 셔틀 시간표 웹 서버 (표준 라이브러리만 사용).

- web/ 폴더의 정적 파일을 제공한다. 시간표는 web/timetable.js 하나만 고치면 되고 재시작이 필요 없다.
- assets/shuttle.apk 가 있으면 /assets/shuttle.apk 로 내려받을 수 있다.
- 바인딩 주소: 환경변수 SHUTTLE_HOSTS (쉼표 구분, 기본 "tailscale,192.168.100.140").
  "tailscale" 은 `tailscale ip -4` 결과. 0.0.0.0 은 외부에 노출되므로 쓰지 않는다.
- 포트: SHUTTLE_PORT (기본 8782)
"""
from __future__ import annotations

import logging
import mimetypes
import os
import subprocess
import sys
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

log = logging.getLogger("shuttle_web")
HERE = Path(__file__).resolve().parent
WEB_DIR = HERE / "web"
ASSET_DIR = HERE / "assets"
PORT = int(os.environ.get("SHUTTLE_PORT", "8782"))
HOSTS = [h.strip() for h in os.environ.get("SHUTTLE_HOSTS", "tailscale,192.168.100.140").split(",") if h.strip()]

MIME = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".json": "application/json; charset=utf-8",
        ".webmanifest": "application/manifest+json", ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
        ".svg": "image/svg+xml", ".ico": "image/x-icon", ".apk": "application/vnd.android.package-archive"}
NO_CACHE = {".html", ".js", ".json", ".webmanifest", ".apk"}   # 시간표 수정이 바로 보이도록


def safe_path(base: Path, rel: str) -> Path | None:
    """base 아래의 파일만 허용. '..' 로 밖으로 나가면 None."""
    try:
        p = (base / rel.lstrip("/")).resolve()
        p.relative_to(base.resolve())
        return p if p.is_file() else None
    except (ValueError, OSError):   # '..' 로 벗어남, NUL 문자, 너무 긴 경로 등
        return None


class Handler(BaseHTTPRequestHandler):
    server_version = "shuttle/1"

    def log_message(self, fmt, *args):
        log.debug("%s " + fmt, self.client_address[0], *args)

    def do_HEAD(self):
        self.do_GET(head=True)

    def do_GET(self, head: bool = False):
        path = urllib.parse.unquote(urllib.parse.urlsplit(self.path).path)
        if path in ("", "/"):
            path = "/index.html"
        if path.startswith("/assets/"):
            f = safe_path(ASSET_DIR, path[len("/assets/"):])
            if f is not None and f.suffix.lower() != ".apk":   # assets/ 는 APK 만 제공
                f = None
        else:
            f = safe_path(WEB_DIR, path)
        if f is None:
            self.send_error(404, "Not Found")
            return
        ext = f.suffix.lower()
        ctype = MIME.get(ext) or mimetypes.guess_type(f.name)[0] or "application/octet-stream"
        data = f.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache" if ext in NO_CACHE else "public, max-age=86400")
        if ext == ".apk":
            self.send_header("Content-Disposition", f'attachment; filename="{f.name}"')
        self.end_headers()
        if not head:
            self.wfile.write(data)


def resolve_host(spec: str) -> str | None:
    if spec != "tailscale":
        return spec
    for attempt in range(20):   # 부팅 직후 tailscaled 가 늦게 뜨는 경우 최대 1분 대기
        try:
            out = subprocess.run(["tailscale", "ip", "-4"], capture_output=True, text=True, timeout=10).stdout.strip()
            if out:
                return out.splitlines()[0].strip()
        except Exception as e:  # noqa: BLE001
            log.warning("tailscale ip 조회 실패(%d): %s", attempt + 1, e)
        time.sleep(3)
    return None


def main() -> int:
    logging.basicConfig(level=logging.DEBUG if "-v" in sys.argv else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if not (WEB_DIR / "index.html").is_file():
        log.error("web/index.html 이 없습니다: %s", WEB_DIR)
        return 1
    servers = []
    failed = []
    # LAN 같은 고정 주소를 먼저 묶고 tailscale 조회(최대 1분 대기)는 뒤로
    for spec in sorted(HOSTS, key=lambda h: h == "tailscale"):
        host = resolve_host(spec)
        if not host:
            log.warning("주소를 못 찾음: %s", spec)
            failed.append(spec)
            continue
        try:
            servers.append(ThreadingHTTPServer((host, PORT), Handler))
            log.info("웹 http://%s:%d", host, PORT)
        except OSError as e:
            log.warning("바인딩 실패 %s:%d: %s", host, PORT, e)
            failed.append(spec)
    if failed:
        # 부팅 직후 주소가 아직 없을 수 있음. 일부만 열린 채 영원히 돌지 않도록 종료 → systemd 가 10초 뒤 재시작
        log.error("바인딩 못 한 주소가 있어 종료합니다 (재시작 대기): %s", ",".join(failed))
        for s in servers:
            s.server_close()
        return 1
    threads = [threading.Thread(target=s.serve_forever, daemon=True) for s in servers]
    for t in threads:
        t.start()
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        pass
    for s in servers:
        s.shutdown()
    return 0


if __name__ == "__main__":
    sys.exit(main())
