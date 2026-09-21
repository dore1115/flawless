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
