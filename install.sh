#!/usr/bin/env bash
# 집 서버에 설치: 파일 복사 + systemd user 유닛 등록. (venv 불필요, 표준 라이브러리만 사용)
# 사용: bash install.sh                      → ~/shuttle 에 복사해서 설치
#       SHUTTLE_DIR=/path bash install.sh    → 지정 경로에 설치 (이 폴더와 같으면 복사 생략)
set -euo pipefail
SRC="$(cd "$(dirname "$0")" && pwd)"
DST="${SHUTTLE_DIR:-$HOME/shuttle}"
DST="$(mkdir -p "$DST" && cd "$DST" && pwd)"

if [ "$SRC" != "$DST" ]; then
  cp "$SRC/shuttle_web.py" "$DST/"
  mkdir -p "$DST/web" "$DST/assets"
  cp "$SRC"/web/* "$DST/web/"
  [ -f "$SRC/assets/shuttle.apk" ] && cp "$SRC/assets/shuttle.apk" "$DST/assets/"
fi

UNIT_DIR="$HOME/.config/systemd/user"
mkdir -p "$UNIT_DIR"
sed "s#%h/shuttle#$DST#g" "$SRC/systemd/shuttle-web.service" > "$UNIT_DIR/shuttle-web.service"
systemctl --user daemon-reload
systemctl --user enable shuttle-web.service
systemctl --user restart shuttle-web.service

if [ "$(loginctl show-user "$USER" -p Linger --value 2>/dev/null)" != "yes" ]; then
  loginctl enable-linger "$USER" 2>/dev/null || echo ">> sudo loginctl enable-linger $USER 를 한 번 실행하세요"
fi

cat <<MSG

설치 완료: $DST
  상태   : systemctl --user status shuttle-web
  로그   : journalctl --user -u shuttle-web -f
  웹     : http://192.168.100.140:8782  /  http://<tailscale ip>:8782
  시간표 : $DST/web/timetable.js 수정 → 새로고침만 하면 반영
  APK    : $DST/assets/shuttle.apk 를 두면 웹 하단에 다운로드 링크
MSG
