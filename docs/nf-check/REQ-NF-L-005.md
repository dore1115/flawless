# REQ-NF-L-005 로그인 응답 지연 측정

- 확인일시: 2026-09-17
- 확인자: 김유하
- 확인 환경: docker (erp-app), k6 부하테스트 도구 (container/k6/login-load-test.js)
- 기준: 동시 사용자 100명, 평균 응답 500ms 초과 또는 p95 1초 초과 시 결함

## 확인 방법
1. 기존 container/k6/load-test.js(근태 조회 API 대상)를 참고하여
   로그인(/login) 전용 스크립트 login-load-test.js 신규 작성
2. 시나리오: 30초간 20명 → 1분간 50명 → 1분간 100명 → 30초 쿨다운 (총 3분)
3. 스프링 시큐리티 CSRF 보호로 인해 최초 curl 테스트에서 403 발생 확인
   → 로그인 페이지(GET /login)에서 CSRF 토큰을 먼저 획득한 뒤
   로그인 요청에 포함하도록 스크립트 수정
4. k6 run container/k6/login-load-test.js 실행

## 결과
THRESHOLDS
http_req_duration
✓ 'p(95)<1000' p(95)=49.92ms
http_req_failed
✓ 'rate<0.05' rate=0.00%

TOTAL RESULTS
checks_succeeded: 100.00% (8104/8104)
http_req_duration: avg=17.73ms, p(90)=34.64ms, p(95)=49.92ms, max=953.12ms
http_req_failed: 0.00%
http_reqs: 16208 (89.65/s)
vus_max: 100


## 판단
- 동시 사용자 100명 기준, p95 응답시간 약 50ms로 기준치(1초) 대비 매우 양호.
- 에러율 0%로 요청 실패 없음.
- → **REQ-NF-L-005 결함 미확인 (정상, 체크리스트상 "미적용" 표기와 실제 상태 불일치 — 확인 필요)**

## 비고
- 최초 테스트 스크립트는 CSRF 토큰 없이 로그인 요청을 보내 100% 실패(403)했음.
  이는 성능 문제가 아니라 스프링 시큐리티의 CSRF 방어가 정상 작동한 것으로,
  스크립트를 CSRF 토큰 포함 방식으로 수정 후 재측정함.