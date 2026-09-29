# REQ-NF-S-001 정책값 하드코딩 확인 및 개선

- 확인일시: 2026-09-25
- 확인자: 김유하
- 확인 환경: docker (erp-app, erp-mysql), 브라우저(일반 창 + 시크릿 창)
- 기준: 비밀번호/로그인/세션 정책값을 코드 수정 없이 변경할 수 있는지

## 개선 전 확인

### 확인 방법
로그인·세션 관련 정책값이 소스코드에 직접 들어가 있는지 검색.
```
Get-ChildItem -Recurse src/main/java | Select-String "maximumSessions|BCryptPasswordEncoder\(|@Size|@Length|min ="
```

### 결과 (담당 코드만 발췌)
| 위치 | 정책값 | 상태 |
|---|---|---|
| SecurityConfig.java 41줄 | 동시 로그인 허용 수 `maximumSessions(1)` | 코드에 직접 작성 |
| SecurityConfig.java 50줄 | BCrypt 강도 `new BCryptPasswordEncoder()` (기본값 10이 코드에 숨어 있음) | 코드에 직접 작성 |
| LoginForm.java 22줄 | 비밀번호 최소 길이 `@Size(min = 8)` | 코드에 직접 작성 |
| application.yaml | 세션 만료 시간 `timeout: 30m` | 이미 설정 파일로 분리됨 |

### 판단
- 동시 로그인 수, BCrypt 강도를 바꾸려면 자바 코드를 수정하고 다시 빌드해야 함.
- → **REQ-NF-S-001 결함 존재 확인 (개선 전 상태)**

---

## 수정 내용

### application.yaml (기존 `app:` 아래에 새 설정 추가, 기존 줄 변경 없음)
```yaml
app:
  timezone: Asia/Seoul
  # REQ-NF-S-001: 로그인/세션 정책값 (코드 수정 없이 여기서 변경)
  security:
    max-sessions: 1       # 한 계정당 동시 로그인 허용 수
    bcrypt-strength: 10   # 비밀번호 암호화 강도
```

### SecurityConfig.java
```java
// 수정 전
.maximumSessions(1)
return new BCryptPasswordEncoder();

// 수정 후
@Value("${app.security.max-sessions:1}") int maxSessions
.maximumSessions(maxSessions)

@Value("${app.security.bcrypt-strength:10}") int bcryptStrength
return new BCryptPasswordEncoder(bcryptStrength);
```
- 설정값이 없을 경우 기존과 동일한 값(1, 10)으로 동작하도록 기본값 지정.

---

## 개선 후 확인

### 확인 방법
자바 코드는 그대로 두고 application.yaml의 `max-sessions` 값만 바꿔서 재시작한 뒤,
같은 계정(emp001)으로 일반 창과 시크릿 창에서 각각 로그인 → 일반 창에서 /attendance 새로고침.

### 결과
| 설정값 | 결과 |
|---|---|
| `max-sessions: 2` | 일반 창 근태 화면 그대로 유지 (동시 로그인 2개 허용) |
| `max-sessions: 1` | 일반 창이 로그인 화면으로 이동 (기존 세션 만료) |

### 판단
- 코드 수정 없이 설정 파일 값만으로 동시 로그인 정책이 바뀌는 것을 확인.
- → **REQ-NF-S-001 수정 완료 (개선 후 상태)**

## 비고
- 비밀번호 최소 길이(`LoginForm`의 `@Size(min = 8)`)는 자바 어노테이션 특성상
  설정 파일 값을 바로 넣을 수 없어 이번 범위에서 제외함 (별도 검증 로직 필요).
- 로그인 실패 횟수·잠금 시간은 기능 자체가 미구현(REQ-F-W-005, W 우선순위)이라 해당 없음.
- 공용 파일(application.yaml)은 기존 `app:` 아래에 새 줄만 추가했으며, 기존 설정은 변경하지 않음.
- 본 브랜치는 `maximumSessions` 코드가 있는 fix-REQ-NF-L-003에서 분기했으므로, L-003(#56) 머지 후 머지 필요.