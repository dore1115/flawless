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


---

# REQ-NF-L-003 수정 후 재검증

- 확인일시: 2026-09-25
- 확인자: (본인 이름)
- 확인 환경: docker (erp-mysql) + IntelliJ 로컬 실행 (port 8081)

## 수정 내용

### (A) 세션 자동 만료 설정
`application.yaml`에 세션 타임아웃 값 추가.
```yaml
server:
  servlet:
    session:
      tracking-modes: cookie
      timeout: 30m
```

### (B) 동시 로그인 제한
`SecurityConfig.java`에 다음을 추가:
- `@EnableWebSecurity` — CSRF 토큰이 로그인 폼에 자동으로 삽입되도록 활성화 (부수적으로 필요했던 설정)
- `.sessionManagement(...)`에 `maximumSessions(1)`, `maxSessionsPreventsLogin(false)`, `expiredUrl("/login?expired")` 추가 — 동일 계정 재로그인 시 기존 세션을 만료시키도록 설정
- `httpSessionEventPublisher()` Bean 추가 — 세션 생성/종료 이벤트를 Spring Security가 인식하도록 연결

### (C) 동시 로그인 제한이 실제로 동작하지 않던 원인 수정
`(B)`만으로는 재현 테스트 시 기존 세션이 끊기지 않는 문제가 있었음. 원인은
`EmployeeUserDetails.java`에 `equals()`/`hashCode()`가 구현되어 있지 않아,
동일 계정(username)으로 로그인해도 매 로그인마다 별개의 객체로 인식되어
Spring Security의 SessionRegistry가 "같은 사용자"로 판단하지 못했기 때문.
`equals()`/`hashCode()`를 username 기준으로 구현하여 해결.

## 확인 방법
PowerShell(Invoke-WebRequest)로 두 개의 독립된 세션을 생성하여 검증함
(로그인 폼 접근 시 화면 자체에는 로그인 여부에 따른 변화가 없어 브라우저
새로고침만으로는 확인이 불가능했음 — REQ-NF-G-003으로 인해 인가 체크가
전역적으로 비활성화되어 있기 때문. 이를 우회하기 위해 `@PreAuthorize`로
보호되는 임시 확인용 엔드포인트를 만들어 검증한 뒤 제거함).

1. 세션 A로 emp001 로그인 → 세션 유지 확인
2. 세션 B로 동일 계정(emp001) 로그인
3. 세션 A로 재요청 → 응답 확인

## 결과
- 세션 A 로그인 직후: 정상 응답 (세션 유지 중)
- 세션 B 로그인 후, 세션 A로 재요청 시: **HTTP 302 (로그인 페이지로 리다이렉트)**
  → 세션 A가 정상적으로 만료(강제 로그아웃)됨을 확인

## 판단
- → **REQ-NF-L-003 수정 완료 확인 (세션 자동 만료 정책 적용, 동시 로그인 시 기존 세션 만료 적용)**

## 비고
- 검증 과정에서 메인 화면(`/`) 접근 시 근태 데이터(`Attendance.clockIn`이 null인 경우)로 인한
  500 에러를 발견함. 이는 L-003 범위 밖(근태 모듈)의 별개 버그로 판단하여 본 브랜치에서는
  수정하지 않고 근태 담당자에게 공유함.