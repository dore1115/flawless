# REQ-NF-M-001 권한 상승 방지 확인 및 개선

- 확인일시: 2026-09-25
- 확인자: 김유하
- 확인 환경: docker (erp-app, erp-mysql), 브라우저(시크릿 창)
- 기준: 일반 사원이 회원 등록 요청으로 관리자 권한 계정을 만들 수 있는지

## 개선 전 확인

### 원인
- `EmployeeRegisterController`에 `@PreAuthorize("hasRole('ADMIN_HR')")`가 붙어 있으나,
  SecurityConfig에 `@EnableMethodSecurity`가 없어 실제로는 동작하지 않음.
- 전역 인가 설정도 `anyRequest().permitAll()` 상태(REQ-NF-G-003)라, 로그인만 하면 누구나 접근 가능.
- 서버가 클라이언트가 보낸 role 값을 그대로 사용하며, 잘못된 값이 오면 500 에러 발생.

### 확인 방법
1. 일반 사원 계정(emp007, role=EMPLOYEE)으로 로그인
2. `/admin/members/register` 접근
3. 아이디 `hacker01`, 권한 `ADMIN_HR`로 계정 등록
4. DB에서 `hacker01`의 role 조회

### 결과
- 일반 사원이 등록 화면에 정상 접근함 (`M-001_전_일반사원_등록화면접근.png`)
- `hacker01` 계정이 `ADMIN_HR` 권한으로 생성됨 (`M-001_전_관리자계정_생성됨.png`)

### 판단
- 일반 사원이 관리자 권한 계정을 직접 만들 수 있음 (권한 상승 가능).
- → **REQ-NF-M-001 결함 존재 확인 (개선 전 상태)**

---

## 수정 내용

### 수정 방식 선택
- `@EnableMethodSecurity`를 추가하면 앱 전체의 `@PreAuthorize`가 켜져,
  다른 담당자의 결함(REQ-NF-H-004, H-005)까지 함께 바뀌어 개선 전/후 측정에 영향을 줌.
- 따라서 담당 파일(회원 등록 Controller/Service) 안에서만 직접 권한을 검사하는 방식으로 수정함.

### EmployeeRegisterController.java
- 로그인 사용자가 `ROLE_ADMIN_HR`인지 확인하는 `checkAdminHr()` 추가
- 등록 화면(GET), 등록 처리(POST), 완료 화면(GET) 세 곳 모두 맨 앞에서 검사
- 인사관리자가 아니면 403 Forbidden 반환

```java
private void checkAdminHr() {
   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
   boolean isAdminHr = auth != null && auth.getAuthorities().stream()
         .anyMatch(a -> "ROLE_ADMIN_HR".equals(a.getAuthority()));
   if (!isAdminHr) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "인사관리자만 접근할 수 있습니다.");
   }
}
```

### EmployeeRegisterService.java
- role 값을 허용된 값(ADMIN_HR/ADMIN_IV/EMPLOYEE)만 받도록 처리
- 잘못된 값이 오면 500 에러 대신 "올바르지 않은 권한입니다." 안내 메시지 표시

```java
// 수정 전
Role roleValue = Role.valueOf(role);

// 수정 후
Role roleValue;
try {
   roleValue = Role.valueOf(role);
} catch (IllegalArgumentException | NullPointerException e) {
   throw new IllegalStateException("올바르지 않은 권한입니다.");
}
```

---

## 개선 후 확인

### 결과
| 테스트 | 계정 | 결과 |
|---|---|---|
| 등록 화면 접근 | emp007 (EMPLOYEE) | **403 Forbidden** (`M-001_후_일반사원_403차단.png`) |
| 등록 화면 접근 + 등록 | emp001 (ADMIN_HR) | 정상 등록 (`M-001_후_인사관리자_정상등록.png`) |

### 판단
- 일반 사원은 회원 등록 화면과 등록 처리에 접근할 수 없고, 인사관리자는 기존과 동일하게 사용 가능.
- → **REQ-NF-M-001 수정 완료 (개선 후 상태)**

## 비고
- `@PreAuthorize`는 삭제하지 않고 유지함. 추후 전역 인가 아키텍처(REQ-NF-G-003) 적용 시
  `@EnableMethodSecurity`가 켜지면 함께 동작하며, 중복 검사여도 문제없음.
- SecurityConfig, 다른 담당자의 Controller/Service는 수정하지 않음.
- 요구사항표의 "정보수정 요청" 경로(사원 정보 수정, HR-002)는 인사 담당 화면이므로 본 항목 범위에서 제외.
- 개선 전 테스트로 생성된 `hacker01` 계정은 로컬 docker DB에만 존재함.
- 본 브랜치는 fix-REQ-NF-L-001에서 분기했으므로, L-001(#51) 머지 후 머지 필요.