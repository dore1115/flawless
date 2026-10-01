# NFR k6 측정 스크립트 (REQ-NF-*)

`02_요구사항정의서 - 비기능 요구사항` 항목별 k6 스크립트.
seed 브랜치(결함)와 fix 브랜치(개선)를 **같은 데이터, 같은 명령**으로 돌려 개선 전/후 수치를 비교한다.

```
nfr/
├─ lib/common.js        로그인(CSRF), 세션, HTML 파싱
├─ lib/profiles.js      부하 프로파일, 서비스 불능 판정, 결과 요약(JSON)
├─ perf/                성능 (부하/breakpoint)
├─ concurrency/         동시성 (Lost Update)
├─ security/            보안 기능 검증 (✓ 개선됨 / ✗ 결함 존재)
├─ data/scale-up.sql    측정용 데이터 증량
└─ results/             실행 결과 JSON (REQ_LABEL_PROFILE_시각.json)
```

## 요구사항 ↔ 스크립트

| REQ | 스크립트 | 판정 방법 |
|---|---|---|
| A-003, A-004 | `perf/A-003-004_attendance.js` | 동일 부하에서 p50/p95/p99, breakpoint RPS |
| I-003, I-004 | `perf/I-003-004_stock-list.js` | 〃 (품목 5,000건 이상에서) |
| H-007 | `perf/H-007_employee-update.js` | 〃 (`MODE=update|list|both`) |
| H-008 | `perf/H-008_code-lookup.js` | 〃 — **아래 "H-008 주의" 먼저 확인** |
| L-005 | `perf/L-005_login.js` | 동시 100명, avg<500ms / p95<1s (threshold로 판정) |
| G-007 | `lib/profiles.js` (모든 perf 스크립트의 `PROFILE`) | 측정 표준 자체 |
| I-005 | `concurrency/I-005_outbound-race.js` | 출고 성공 건수 == 실제 재고 감소량, 초과 출고 0 |
| H-009 | `concurrency/H-009_lost-update.js` | 같은 스냅샷 기반 동시 저장 중 성공 1건 |
| G-001, G-003, A-002, H-004, H-005, I-002, G-004, A-001/H-001/I-001/G-002, L-002, L-003, L-004 | `security/security-checks.js` | check ✓/✗ |
| H-002, M-001 | `security/security-checks.js` + `ALLOW_WRITES=true` | 결함 상태면 데이터가 실제로 생성됨 |

**k6 대상이 아닌 항목**
- L-001 비밀번호 평문 저장 → `SELECT username, password FROM employee LIMIT 5;`
- G-005 접근 로그 / H-005 감사 로그 → 변경 후 `employee_history` 등 테이블 조회
- G-006 로그 민감정보 → `docker logs erp-app | grep -E "password|주민"`
- S-001 정책값 하드코딩 → 설정 변경만으로 동작이 바뀌는지 확인
- H-003 → 기능 개발 안 함 / **H-006 → 승인 목록 조회 엔드포인트가 아직 어떤 브랜치에도 없음** (구현되면 perf 스크립트를 복사해 URL만 바꾸면 됨)

## 사전 준비

1. **데이터 증량** (1회): `data/scale-up.sql` — 품목 +5,000, 근태 5년치. 개선 전/후는 이 상태의 같은 DB에서 측정.
2. **Grafana의 서버 측 p99**: `http_server_requests_seconds`로 p99를 그리려면 `application.yaml`에
   `management.metrics.distribution.percentiles-histogram.http.server.requests: true`가 있어야
   `histogram_quantile()`에 쓸 버킷이 생긴다. (없으면 Grafana p99는 k6 쪽 값만 믿을 것)
3. k6를 앱과 같은 머신에서 돌리면 k6 자체가 CPU를 먹는다. 앱은 1core로 제한돼 있으니 괜찮지만, k6 컨테이너에는 제한을 걸지 말 것.

## 실행

```bash
cd container/k6/nfr

# 1) smoke — 스크립트 동작 확인 + 무부하 기준선
k6 run perf/A-003-004_attendance.js

# 2) load — 개선 전/후 "같은 부하" 비교 (RATE req/s 고정, 3분)
LABEL=seed PROFILE=load RATE=20 k6 run perf/A-003-004_attendance.js
LABEL=fix  PROFILE=load RATE=20 k6 run perf/A-003-004_attendance.js

# 3) breakpoint — 서비스 불능 지점 탐색 (5 → 100 req/s, 5씩, 계단당 60초)
LABEL=seed PROFILE=breakpoint START_RPS=5 STEP_RPS=5 MAX_RPS=100 k6 run perf/I-003-004_stock-list.js

# 동시성 / 보안
k6 run concurrency/I-005_outbound-race.js            # ITEM_ID=23 RACE_VUS=50 ITER=2
k6 run concurrency/H-009_lost-update.js              # TARGET_ID=50 RACE_VUS=10
k6 run security/security-checks.js                   # HTTP_URL=http://localhost:8080 (G-001 확인용)
```

도커 네트워크 안에서 돌릴 때 (compose 네트워크 이름은 `docker network ls`로 확인):
```bash
docker run --rm -i --network container_erp-net -v "$PWD":/nfr -w /nfr \
  -e BASE_URL=http://app:8080 -e LABEL=seed -e PROFILE=breakpoint grafana/k6 run perf/A-003-004_attendance.js
```

### 주요 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | G-001 수정 후엔 `https://...` (자체 서명 인증서 허용됨) |
| `LABEL` | `seed` | 결과 파일명/태그. `seed`, `fix`, `alt` 등 |
| `PROFILE` | `smoke` | `smoke` / `load` / `breakpoint` / `vus` |
| `RATE`, `DURATION` | 스크립트별 | load 프로파일 도착률, 시간 |
| `START_RPS`,`STEP_RPS`,`MAX_RPS`,`STEP_HOLD_S` | 5, 5, 100, 60 | breakpoint 계단 |
| `P99_LIMIT_MS` | `3000` | 서비스 불능 p99 기준 |
| `ERR_LIMIT` | `0.05` | 서비스 불능 에러율 기준 |
| `SLO_P95_MS` | `1000` | 정상 서비스 목표(SLO) |
| `MAX_VUS` | `1000` | arrival-rate가 쓸 수 있는 최대 VU |
| `ABORT_ON_BREAK` | `false` | `true`면 불능 판정 계단에서 즉시 중단 |

### 스크립트별 전용 변수 (해당 스크립트 안에서 `__ENV`로 읽음)

| 스크립트 | 변수 | 기본값 | 설명 |
|---|---|---|---|
| A-003-004 | `FROM_YM`, `TO_YM` | `2025-09`, `2026-09` | 조회할 월 범위 |
| I-003-004 | `MAX_PAGE`, `SEARCH_RATIO`, `KEYWORDS` | `4`, `0.3`, `모니터,케이블,용지,부하테스트` | 페이지 범위(품목 수/10 − 1), 검색 비율, 검색어 |
| H-007 | `MODE` | `update` | `update`(info 조회 + 변경 POST) / `list`(사원 목록) / `both` |
| H-007 | `ID_FROM`, `ID_TO` | `7`, `100` | 대상 사원 ID 범위 |
| L-005 | `LOGOUT` | `true` | 매 반복 로그아웃 여부 (false면 세션 누적) |
| I-005 | `ITEM_ID`, `RACE_VUS`, `ITER`, `RESTORE` | `23`, `50`, `2`, `true` | 대상 품목, 동시 VU 수, VU당 요청 수, 종료 후 재고 복원 |
| H-009 | `TARGET_ID`, `RACE_VUS` | `50`, `10` | 대상 사원, 동시 저장 수 |
| security | `ALLOW_WRITES`, `HTTP_URL`, `TARGET_EMP_ID`, `ITEM_ID`, `EXPECT_CONCURRENT`, `SESSION_WAIT_S` | `false`, -, `50`, `1`, `block`, `0` | 쓰기 검사 허용, 평문 포트, 대상 ID, 동시 로그인 기대 정책, 세션 만료 대기 |

예: `MODE=list PROFILE=load RATE=10 k6 run perf/H-007_employee-update.js`

## 서비스 불능 판정 기준

breakpoint의 **계단 하나(60초 유지 구간)** 단위로 판정하고, 아래 중 하나라도 해당되면 그 계단이 불능 지점이다.

1. **p99 ≥ 3초** (`P99_LIMIT_MS`)
2. **에러율 ≥ 5%** (`ERR_LIMIT`)
3. **달성 처리량 < 목표의 90%** — 서버가 요청을 못 받아 k6 쪽에서 dropped가 나는 포화 상태

결과에는 "서비스 불능 지점 RPS"와 "마지막 안정 구간 RPS + 그때의 p99"가 같이 찍힌다.
보고서에는 **마지막 안정 구간 RPS(= 최대 안정 처리량)** 를 개선 전/후 비교 지표로 쓰는 것을 권장.

> ⚠ k6 threshold는 테스트 **전체 누적**으로 계산되기 때문에, 전체 p99만 보면 불능 시점이 늦게 잡힌다.
> 그래서 계단별로 `stage` 태그를 붙여 구간별 p99를 따로 계산한다. Grafana에서도 `rate(...[1m])` 같은 1분 창으로 볼 것.

## H-008 주의 (재현이 안 될 수 있음)

`schema.sql`의 `employee.position_id`, `department_id`에는 FK가 걸려 있고, **InnoDB는 FK 컬럼에 인덱스가 없으면 자동으로 만든다.**
즉 지금 스키마에선 "인덱스 부재" 결함이 실제로는 없을 가능성이 크다. 측정 전에 확인:

```sql
SHOW INDEX FROM employee;
EXPLAIN SELECT * FROM employee WHERE position_id = 1;   -- type=ref, key=fk_employee_position 이면 이미 인덱스 사용 중
```

결함을 재현하려면 seed 쪽에서 FK를 빼야 한다 (`ALTER TABLE employee DROP FOREIGN KEY fk_employee_position, DROP INDEX fk_employee_position;` 부서도 동일).
그리고 사원 100명으론 풀스캔도 빨라서 차이가 안 보이니 `scale-up.sql`의 [3] 사원 50,000명 블록도 켤 것.

단, 50,000명을 직책 6개에 고르게 나누면 직책 하나당 약 8,000명이라, 요청 비용의 대부분이
"인덱스냐 풀스캔이냐"가 아니라 **8,000개 엔티티를 올리는 비용**이 된다 (인덱스를 써도 8,000행은 읽어야 함).
인덱스 효과를 보이려면 **소속 사원이 1~5명뿐인 직책/부서**를 따로 만들어 그 ID만 대상으로 측정할 것
(인덱스: 몇 행만 읽음 / 풀스캔: 50,000행 전부 읽음 → 차이가 극대화됨).

차단된 삭제는 소속 사원 목록을 flash attribute로 세션에 저장한다. 스크립트는 POST 뒤 목록 GET을 한 번 따라가서
(측정 제외) 이를 비운다. `FOLLOW_REDIRECT=false`로 끄면 세션에 목록이 쌓여 힙이 차고 CPU 100%가 테스트 후에도 유지되니 끄지 말 것.

## 동시성 스크립트가 보는 것

- **I-005**: 락이 없으면 두 요청이 같은 재고값을 읽고 각자 `-1`해서 저장 → 출고 이력은 2건인데 재고는 1만 빠짐.
  `nfr_lost_update_count`(= 성공 건수 − 실제 감소량)와 `nfr_oversold_count`가 0이어야 개선된 것. 끝나면 입고로 재고를 되돌린다.
- **H-009**: 관리자 여럿이 같은 화면을 열어놓고 동시에 저장하는 상황. 낙관적 락 + 폼 hidden `version`이면 1건만 성공해야 한다.
  비관적 락만 걸고 폼에 version을 싣지 않으면 순서만 직렬화될 뿐 덮어쓰기는 그대로 일어난다.
  성공 판정은 "302 → info"로 하고 있으니, 충돌을 같은 화면 + 플래시로 처리했다면 판정 조건을 맞춰 고칠 것.
