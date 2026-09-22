# REQ-NF-L-001 비밀번호 평문 저장 확인

- 확인일시: 2026-09-17
- 확인자: (김유하)
- 확인 환경: docker (erp-mysql, mysql:8.0)
- 확인 방법: employee 테이블 password 컬럼 직접 조회

## 실행 명령어
```sql
USE erp;
SELECT username, password FROM employee;
```

## 결과 (일부 발췌)
+----------+--------------+
| username | password |
+----------+--------------+
| emp001 | password123! |
| emp002 | password123! |
| emp003 | password123! |
...
+----------+--------------+
100 rows in set


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
- 확인자: (본인 이름)
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
2. seed_data.sql은 REQ-NF-L-001 결함 재현용 공용 테스트 데이터이므로 수정하지 않고,
   로컬 DB에서만 employee.password 컬럼을 BCrypt 해시값으로 직접 UPDATE하여 검증 진행
3. 로그인 재시도

## 실행 명령어
```sql
UPDATE employee SET password = '$2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6';
SELECT username, password FROM employee LIMIT 3;
```

## 결과 (일부 발췌)
+----------+----------------------------------------------------------------+
| username | password |
+----------+----------------------------------------------------------------+
| emp001 | $2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6 |
| emp002 | $2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6 |
| emp003 | $2b$12$OaWJmF/jiY8qTiQ0BBvViOkzxJbywDWFxayFmHHwYH.p6Zw4K24y6 |
+----------+----------------------------------------------------------------+
- password 컬럼이 BCrypt 해시 형태(`$2b$12$...`)로 저장됨을 확인.
- 로그인 화면에서 아이디 `emp001`, 비밀번호 `password123!`(원본 평문)로 정상 로그인 성공 확인.

## 판단
- → **REQ-NF-L-001 수정 완료 확인 (해시 알고리즘 적용, 개선 후 상태)**
- 비밀번호는 해시로 저장되지만 사용자는 기존과 동일한 평문 비밀번호로 로그인 가능함을 확인하여,
  기능 정상 동작과 보안 개선이 동시에 충족됨을 검증함.

## 비고
- seed_data.sql 자체는 결함 재현 목적상 평문 상태를 유지하도록 두었으며,
  로컬 검증 시에만 DB 값을 수동으로 해시로 치환함(Git 커밋 대상 아님).
- 새 계정을 등록(AU-002)하거나 seed를 재적용(`docker compose down -v` 후 재기동)하면
  평문 상태로 되돌아가므로, 회원가입/등록 로직 자체에서도 BCryptPasswordEncoder가
  올바르게 적용되는지는 별도 확인 필요(현재 확인 범위는 로그인 인증 경로에 한정).