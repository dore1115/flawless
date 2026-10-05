# REQ-NF-L-001 비밀번호 평문 저장 확인

- 확인일시: 2026-09-17
- 확인자: 김유하
- 확인 환경: docker (erp-mysql, mysql:8.0)
- 확인 방법: employee 테이블 password 컬럼 직접 조회

## 실행 명령어
```sql
USE erp;
SELECT username, password FROM employee;
```

## 결과 (일부 발췌)
```
+----------+--------------+
| username | password     |
+----------+--------------+
| emp001   | password123! |
| emp002   | password123! |
| emp003   | password123! |
...
+----------+--------------+
100 rows in set
```

## 판단
- password 컬럼에 `password123!` 형태의 평문 비밀번호가 그대로 저장되어 있음을 확인.
- BCrypt 등 해시 알고리즘 미적용 상태.
- → **REQ-NF-L-001 결함 존재 확인 (미적용, 개선 전 상태)**

## 비고
- 문서(`requirements/employee-auth-requirements.md`)상 컬럼명은 `login_id`였으나,
  실제 DB 컬럼명은 `username`으로 확인됨 (문서-실제 스키마 불일치, 별도 확인 필요).

---

# REQ-NF-L-001 수정 후 재검증

- 확인일시: 2026-09-22
- 확인자: 김유하
- 확인 환경: docker (erp-mysql, mysql:8.0) + IntelliJ 로컬 실행 (port 8081)

## 수정 내용
`SecurityConfig.java`의 `PasswordEncoder` Bean을 자체 구현 평문 인코더에서
`BCryptPasswordEncoder`로 교체.

```java
// 수정 전
@Bean
public PasswordEncoder passwordEncoder() {
   return new PasswordEncoder() {
      @Override
      public String encode(CharSequence rawPassword) {
         return rawPassword.toString();
      }
      @Override
      public boolean matches(CharSequence rawPassword, String encodedPassword) {
         return rawPassword.toString().equals(encodedPassword);
      }
   };
}

// 수정 후
@Bean
public PasswordEncoder passwordEncoder() {
   return new BCryptPasswordEncoder();
}
```

## 확인 방법
1. 위 코드 수정 후 기존 평문 비밀번호로는 로그인이 불가함을 확인
   (BCrypt는 해시값끼리만 비교 가능하므로, 평문으로 남아있는 seed 데이터와 매칭 불가)
2. seed_data.sql은 팀 공용 파일이므로 수정하지 않고,
   로컬 DB에서만 employee.password 컬럼을 BCrypt 해시값으로 직접 UPDATE하여 검증 진행
3. 로그인 재시도

## 실행 명령어
```sql
UPDATE employee SET password = '$2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6';
SELECT username, password FROM employee LIMIT 3;
```

## 결과 (일부 발췌)
```
+----------+--------------------------------------------------------------+
| username | password                                                     |
+----------+--------------------------------------------------------------+
| emp001   | $2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6 |
| emp002   | $2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6 |
| emp003   | $2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6 |
+----------+--------------------------------------------------------------+
```
- password 컬럼이 BCrypt 해시 형태(`$2b$12$...`)로 저장됨을 확인.
- 로그인 화면에서 아이디 `emp001`, 비밀번호 `password123!`(원본 평문)로 정상 로그인 성공 확인.

## 판단
- → **로그인 인증 경로의 해시 비교 적용 확인**
- 비밀번호는 해시로 저장되지만 사용자는 기존과 동일한 평문 비밀번호로 로그인 가능함을 확인.
- ※ 확인 범위가 로그인 인증 경로에 한정됨. 회원 등록 경로는 2026-09-25 추가 검증에서 보완함(아래 참고).

## 비고
- seed_data.sql은 팀 공용 파일이므로 수정하지 않고, 로컬 검증 시에만 DB 값을 수동으로 해시로 치환함(Git 커밋 대상 아님).
- 새 계정을 등록(AU-002)했을 때도 해시로 저장되는지는 별도 확인 필요 → 2026-09-25 추가 검증에서 확인.

---

# REQ-NF-L-001 추가 검증 및 보완 (회원 등록 경로 + 로그 노출)

- 확인일시: 2026-09-25
- 확인자: 김유하
- 확인 환경: docker (erp-app, erp-mysql)

## (A) 회원 등록 시 비밀번호 저장 방식

### 발견 사항
- 09-22 수정은 로그인 인증 경로(PasswordEncoder Bean)만 BCrypt로 교체한 것이었음.
- 회원 등록(AU-002, /admin/members/register)으로 새 계정을 만들어 확인한 결과,
  EmployeeRegisterService가 비밀번호를 그대로 저장하여 **평문으로 저장**되고 있었음.
  (해당 계정은 BCrypt 비교로 인해 로그인도 불가능한 상태)

### 수정 내용
`EmployeeRegisterService.java`에 PasswordEncoder를 주입하고, 저장 시 해시로 변환.
```java
// 수정 전
.password(password)
// 수정 후
.password(passwordEncoder.encode(password))
```

### 확인 방법
- 같은 비밀번호(`Test1234!`)로 수정 전 2개(test001, test002), 수정 후 2개(test003, test004) 계정 등록 후 DB 조회

### 결과
| username | password |
|---|---|
| test001 (수정 전) | Test1234! |
| test002 (수정 전) | Test1234! |
| test003 (수정 후) | $2a$10$Nl/zlwbc1DNkyzubLvF4QeNWkPyNaloCIrrjIXTDGMpFHscbVjznO |
| test004 (수정 후) | $2a$10$.xyCvfEaRTc0j9nCfXXSZed66LopFzo0ZcOumChzOZNiHqnii.APq |

- 수정 후 계정은 BCrypt 해시로 저장되며, 같은 비밀번호라도 해시값이 서로 다름(salt 적용 확인).
- test003 계정으로 로그인 성공 확인.

## (B) 로그인 시 비밀번호 로그 노출 (L-001 부가 결함)

### 수정 내용
`EmployeeUserDetailsService.java`에서 `log.info("password = {}", ...)` 줄 제거.

### 결과
| 구분 | 로그 확인 결과 (`docker logs erp-app`) |
|---|---|
| 수정 전 | `password = $2b$12$OaWJ...` 출력됨 |
| 수정 후 | password 로그 없음 (`username = emp001`은 정상 출력되어 로그인 정상 확인) |

## 판단
- 로그인 인증, 회원 등록 저장, 로그 출력 세 경로 모두 평문 비밀번호 노출이 제거됨.
- → **REQ-NF-L-001 수정 완료 (개선 후 상태)**

## 비고
- seed_data.sql은 팀 공용 파일이므로 수정하지 않음. BCrypt 적용 후 seed 평문 계정은 로그인 불가하므로 처리 방식은 팀 회의에서 결정 예정.
- 검증용 DB 값 변경(emp 계정 해시 UPDATE, test 계정 등록)은 로컬 docker DB에만 적용됨.
- 로그인 후 메인 화면(/) 500 에러는 근태 모듈 문제로, 담당자에게 공유된 사항임.