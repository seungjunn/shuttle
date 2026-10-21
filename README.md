# 꿈에그린 셔틀 — 아파트 셔틀버스 시간표 웹/앱

꿈에그린단지아파트 셔틀버스 시간표(2026-09-28 배포본)를 휴대폰에서 바로 보는 페이지입니다.
"내 정류장"을 고르면 **다음 셔틀까지 남은 시간**을 보여 주고, 주말·공휴일·운행 종료를 알려 줍니다.
하기숲 예약 봇과 같은 방식으로 집 리눅스 서버에서 systemd 로 띄우고(LAN + Tailscale), 그 페이지를 감싼
안드로이드 앱(APK)을 배포합니다. 앱에는 페이지가 통째로 내장되어 있어 **서버가 안 닿아도 시간표가 보입니다.**

```
web/index.html          페이지 전체 (HTML/CSS/JS 한 파일, 외부 의존성 없음)
web/timetable.js        ★ 시간표 데이터. 이것만 고치면 됨 (서버 재시작 불필요)
web/original.jpg        시간표 원본 사진
web/manifest.webmanifest, icon-*.png   홈 화면 추가(PWA) 용
shuttle_web.py          정적 웹 서버 (Python 표준 라이브러리만). Tailscale/LAN 주소에 바인딩, 포트 8782
systemd/shuttle-web.service
install.sh              서버 설치 (~/shuttle 로 복사 + systemd user 유닛 등록)
android/                WebView 래퍼 앱 (kr.luckykoopa.shuttle). web/ 를 assets 로 내장
build_apk.sh            web/ → android assets 복사 → 서명된 APK → assets/shuttle.apk
assets/shuttle.apk      (빌드 산출물, git 제외) 서버에 두면 웹 하단에 다운로드 링크
시간표/                  원본 사진
```

## 페이지 기능

- **상단**: 오늘 날짜·시각, 운행 여부(월~금 운행 / 주말 운행 없음 / 공휴일 운행 없음 + 다음 운행일),
  **이번주 차량 배정**(1단지 차량·2단지 차량이 어느 호차 노선인지. 매주 교대).
- **다음 셔틀**: 정류장 선택(기억됨) → 다음 출발 시각, 남은 시간, 회차·호차, 이어지는 3편.
  운행 종료/휴일이면 다음 운행일 첫차.
- **지금 버스 위치**: 순환 노선을 가로로 긴 둥근 고리로 그리고, 운행 중인 버스가 어느 정류장 사이에 있는지 고리 위의 버스 마커로 표시(시간표 기준 추정, GPS 아님). 5초마다 부드럽게 이동.
  낮·오후는 정류장별 시각으로 보간하고, 오전 회차는 경유 시각이 없어 출발 후 25분 안에 균등하게 지나간다고 가정("추정" 표기).
  운행 중인 버스가 없으면 다음 출발 버스를 출발지에 대기로 표시.
- **새로고침**: 헤더 ↻ 버튼. 10분마다(30초 이상 조작이 없을 때)와 화면에 다시 돌아왔을 때(5분 이상 지났으면) 자동으로 다시 불러와 서버의 시간표 변경을 반영.
- **오전** 탭: 1~9회차 경유지. 비고의 "07:53 1호차만, 08:05 2호차만, 08:20 1호차만" 에 따라 5·6·9회차는 운행 안 함으로 표시.
- **낮·오후** 탭: 10~26회차 12개 정류장 표. 가로 스크롤, 회차 열 고정, 지난 회차 흐리게, 다음 회차 노랑, 내 정류장 열 파랑. 17:30·18:20 지연 가능 ⚠.
- **안내** 탭: 비고 전문, 공휴일 목록. **원본** 탭: 사진.
- 09시 전에는 오전 탭, 그 뒤엔 낮·오후 탭이 먼저 열림. 다크 모드 자동(하단 "테마"로 고정 가능).
- 확인용 URL 파라미터: `?t=2026-10-22T07:20` (시각 바꿔 보기), `?tab=morning|day|info|orig`, `?stop=반석초`, `?theme=dark|light`.

## 시간표 고치기 — `web/timetable.js`

JSON 모양의 자바스크립트 파일입니다. 고친 뒤 페이지를 새로고침하면 바로 반영됩니다(서버 재시작 불필요).
앱 내장본까지 갱신하려면 APK 를 다시 빌드합니다.

| 키 | 내용 |
|---|---|
| `morning[]` | 오전 회차. `stops` 는 `[정류장, "HH:MM" 또는 ""]` 순서. `suspended` 에 문구를 넣으면 운행 안 함으로 표시 |
| `day.stops`, `day.trips[]` | 낮·오후 12개 정류장 이름과 회차별 12개 시각. `note` 는 ⚠ 문구 |
| `holidays` | `"YYYY-MM-DD": "이름"`. 2026·2027 공휴일(대체공휴일 포함)이 들어 있음. 매년 추가 |
| `rotation.anchor_monday` | 차량 배정 기준 주의 월요일. 사진 날짜(2026-09-28) 주에 "1단지 차량 = 1호차" 였다고 가정. **실제와 반대면 이 날짜를 한 주 앞뒤로 옮기세요** |
| `notes[]` | 안내 탭 비고 |

## 서버 설치 (집 서버)

```bash
git clone https://github.com/seungjunn/shuttle.git ~/git/shuttle
cd ~/git/shuttle && SHUTTLE_DIR=$PWD bash install.sh # 생략하면 ~/shuttle 로 복사해서 설치
systemctl --user status shuttle-web
journalctl --user -u shuttle-web -f
```

업데이트: `cd ~/git/shuttle && git pull` (집 서버 `~/git/shuttle` 은 이 저장소의 clone). git 이 없는 서버라면 이 PC 에서 압축해 복사합니다:

```bash
# 이 PC 에서
tar czf /tmp/shuttle-deploy.tar.gz --transform 's#^#shuttle/#' shuttle_web.py install.sh systemd web assets/shuttle.apk README.md
scp -P 9812 /tmp/shuttle-deploy.tar.gz seungjunn@seungjunn.iptime.org:/tmp/
ssh -p 9812 seungjunn@seungjunn.iptime.org 'cd ~/git && tar xzf /tmp/shuttle-deploy.tar.gz && rm /tmp/shuttle-deploy.tar.gz && cd shuttle && SHUTTLE_DIR=$PWD bash install.sh'
```

시간표·페이지만 바뀌었으면 복사만으로 반영되고, `shuttle_web.py` 가 바뀌었으면 `install.sh` 가 서비스를 재시작합니다.

- 주소: `http://192.168.100.140:8782` (집 안) / `http://100.86.158.27:8782` (Tailscale). 포트 8781 은 하기숲이 쓰므로 8782.
- 바인딩 주소·포트는 유닛 파일의 `SHUTTLE_HOSTS`, `SHUTTLE_PORT` 환경변수. `0.0.0.0` 은 쓰지 않습니다.
- 인증 없음(시간표는 비밀이 아님). HTTP 이므로 공유기 포트포워딩으로 외부에 열지 마세요.
- `web/` 아래 파일만 제공하고 `..` 경로는 거부합니다. `assets/shuttle.apk` 가 있으면 `/assets/shuttle.apk` 로 내려받기.

## 안드로이드 앱

`android/` 는 하기숲 앱과 같은 WebView 래퍼입니다. 집 안 주소 → Tailscale 주소 순으로 열고, 둘 다 실패하면
앱에 내장된 `assets/web/index.html` 을 엽니다(토스트로 안내). 당겨서 새로고침하면 다시 서버부터 시도합니다.

빌드 (JDK 17, Android SDK platform 34 + build-tools 34.0.0, Gradle 8.7 — 하기숲과 같은 환경):

```bash
./build_apk.sh            # → assets/shuttle.apk (약 1.1 MB)
```

- 서명 키 `android/keystore/shuttle.jks` (비밀번호는 `keystore/keystore.properties`). git 제외. **같은 키로 계속 서명해야 덮어쓰기 설치**가 됩니다.
- 서버 주소가 바뀌면 `MainActivity.java` 의 `URLS`, `OUR_HOSTS` 와 `res/xml/network_security_config.xml` 을 고치고 `versionCode` 를 올립니다.
- 설치: APK 를 휴대폰으로 보내거나 웹 하단 "안드로이드 앱(APK) 받기" → "알 수 없는 앱 설치 허용" 후 열기.
- 아이폰/다른 사람: 브라우저로 주소를 열고 "홈 화면에 추가" 하면 앱처럼 쓸 수 있습니다(PWA 매니페스트 포함).

## 알려진 제약

- 공휴일은 파일에 적힌 목록으로만 판단합니다(2026·2027 수록). 임시공휴일이나 반석초 방학 단축 운행은 반영되지 않으므로 직접 추가하세요.
- 차량 배정(1단지/2단지 차량 ↔ 1호차/2호차)은 사진 날짜 기준 추정입니다. 노선·시각 자체는 영향이 없습니다.
- 앱 내장본은 빌드 시점의 시간표입니다. 서버가 되는 곳에서 열면 항상 서버 최신본을 봅니다.
- 선택한 정류장·테마는 브라우저 저장소에 주소별로 저장되므로 집 안 주소, Tailscale 주소, 앱 내장본에서 각각 따로 기억됩니다.
- 서버 시작 시 LAN 이나 Tailscale 주소를 못 묶으면 프로그램이 종료하고 systemd 가 10초 뒤 다시 시도합니다(부팅 직후 정상). `journalctl` 에 "바인딩 못 한 주소" 가 계속 찍히면 `SHUTTLE_HOSTS` 를 확인하세요.
