# REQ-NF-L-004 로그아웃 후 세션 무효화 확인

- 확인일시: 2026-09-17
- 확인자: 김유하
- 확인 환경: docker (erp-app), curl + 브라우저 병행 테스트

## 확인 방법
1. 브라우저에서 emp001 계정으로 로그인, JSESSIONID 쿠키 값 확보
2. 로그아웃 전, 해당 JSESSIONID로 보호된 페이지(/attendance) 접근 테스트
3. 브라우저에서 로그아웃 실행
4. 로그아웃 후, 동일한 JSESSIONID로 같은 페이지 재접근 테스트

## 참고: 테스트 대상 페이지 선정 관련
- 최초 시도한 /admin/employees는 실제 존재하지 않는 경로(404)로 확인되어 제외.
- /hr/employees는 HTML 주석상 REQ-NF-G-003/REQ-NF-H-004 결함으로 인해
  로그인 여부와 무관하게 접근 가능한 것으로 명시되어 있어(쿠키 없이도 200 확인),
  본 항목(세션 무효화) 테스트 대상으로 부적합하여 제외.
- 로그인 필수 여부가 확인된 /attendance로 테스트 진행.

## 결과

### 로그아웃 전
curl.exe -i http://localhost:8080/attendance -H "Cookie: JSESSIONID=0C2DD01162A80BA2927F29D3BC6CEFD3"
→ HTTP/1.1 200 (근태관리 화면 정상 응답)


### 로그아웃 후 (동일 JSESSIONID로 재요청)
curl.exe -i http://localhost:8080/attendance -H "Cookie: JSESSIONID=0C2DD01162A80BA2927F29D3BC6CEFD3"
→ HTTP/1.1 302 (Location: http://localhost:8080/, 로그인 페이지로 리다이렉트)


## 판단
- 로그아웃 후 동일 세션으로 재요청 시 302(비로그인 상태로 처리)로 응답함.
- 로그아웃 시 세션이 정상적으로 무효화되고 있는 것으로 확인됨.
- → **REQ-NF-L-004 결함 미확인 (정상 동작, 체크리스트상 "미적용" 표기와 실제 상태 불일치 — 확인 필요)**


---

# REQ-NF-L-004 재검증 — 코드 수정 없음 확인

- 확인일시: 2026-09-25
- 확인자: (본인 이름)
- 확인 환경: docker (erp-mysql) + IntelliJ 로컬 실행 (port 8081)

## 배경
L-003 작업(SecurityConfig.java의 sessionManagement 관련 수정)으로 인해
로그아웃 기능이 영향받지 않았는지 확인 차원에서 재검증 진행.

## 확인 방법
PowerShell(Invoke-WebRequest)로 세션을 생성하여 검증함.
1. emp001 로그인
2. 로그인 상태에서 /attendance 접근 (로그인 필수 페이지, 세션 유효성 확인용)
3. 로그아웃 요청 (CSRF는 메타 태그 방식(`X-CSRF-TOKEN` 헤더)으로 전달)
4. 로그아웃 후 동일 세션으로 /attendance 재접근

## 결과
| 시점 | 요청 | 응답 |
|---|---|---|
| 로그아웃 전 | GET /attendance | 200 |
| 로그아웃 | POST /logout | 302 (Location: /login) |
| 로그아웃 후 | GET /attendance (동일 세션) | 302 (Location: /login) |

## 결론
- L-003 수정 이후에도 로그아웃 시 세션이 정상적으로 무효화됨을 재확인.
- **코드 수정 불필요. REQ-NF-L-004는 결함 미해당으로 최종 처리함.**