# REQ-NF-L-003 세션 만료 / 동시 로그인 정책 확인

- 확인일시: 2026-09-17
- 확인자: 김유하
- 확인 환경: docker (erp-app, erp-mysql), Grafana + Prometheus 지표 활용

## (A) 세션 자동 만료 설정 확인

### 확인 방법
`application.yml` 내 `server.servlet.session.timeout` 설정 여부 확인

### 결과
```yaml
server:
  servlet:
    session:
      tracking-modes: cookie
      # timeout: 30m  ← 주석 처리되어 있음, 설정값 없음
```

### 판단
- 세션 타임아웃 값이 설정되어 있지 않음. 기본값(서버/컨테이너 설정 의존)으로 동작 중.
- → **세션 자동 만료 정책 미확정 상태 확인 (결함 존재)**

---

## (B) 동시 로그인 제한 확인

### 확인 방법
1. Prometheus 지표(`tomcat_sessions_active_current_sessions`)를 Grafana에 패널로 시각화
2. 동일 계정(emp001)으로 일반 브라우저 창 + 시크릿 창에서 각각 로그인 시도
3. Grafana 그래프에서 활성 세션 수 변화 관찰

### 결과
- 일반 창 로그인 → 활성 세션 수 0 → 1
- 시크릿 창에서 동일 계정 추가 로그인 → 활성 세션 수 1 → 2
- (Grafana 대시보드 "NF 체크 대시보드" > "활성 세션 수" 패널, 스크린샷 첨부)

### 판단
- 동일 계정으로 두 곳에서 동시 로그인이 모두 성공함 (세션 2개 동시 존재).
- 동시 로그인 제한 기능 없음.
- → **REQ-NF-L-003 결함 존재 확인 (미적용, 개선 전 상태)**

## 종합 결론
- 세션 자동 만료 정책, 동시 로그인 제한 정책 모두 미적용 상태로 확인됨.
- 개선 라운드에서 `timeout` 값 설정 및 동시 세션 제어(예: Spring Security `maximumSessions`) 적용 후 동일 방식으로 재측정 필요.