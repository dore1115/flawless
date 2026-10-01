// ─────────────────────────────────────────────────────────────
// 보안 결함 기능 검증 (부하 X, 1 VU 1회 실행)
// 각 check = "개선 후 기대 동작". ✓ = 개선됨 / ✗ = 결함 존재
//   → seed 브랜치에서 ✗, fix 브랜치에서 ✓ 가 나오면 개선 전/후 증거가 된다.
//
// 부작용 최소화 원칙: 인가 검사는 "검증에 걸리는 잘못된 값"이나 "삭제가 차단되는 대상"으로 보낸다.
//   인가가 없으면 400/폼 재표시로 끝나고(데이터 변화 없음), 인가가 있으면 403.
// 데이터를 실제로 만들 수 있는 검사(H-002, M-001)는 ALLOW_WRITES=true 일 때만 실행한다.
//
// 실행: k6 run security/security-checks.js
// ─────────────────────────────────────────────────────────────
import http from "k6/http";
import { check, group, sleep } from "k6";
import { BASE_URL, PASSWORD, USERS, login, clearCookies, csrfFrom, withCsrf, location, aux, doc } from "../lib/common.js";
import { commonOptions, makeSummary } from "../lib/profiles.js";

const ALLOW_WRITES = (__ENV.ALLOW_WRITES || "false") === "true";
const HTTP_URL = __ENV.HTTP_URL || ""; // G-001: 평문 포트 (예: http://localhost:8080) — 있으면 https 리다이렉트 여부 확인
const TARGET_EMP_ID = __ENV.TARGET_EMP_ID || "50";
const ITEM_ID = __ENV.ITEM_ID || "1";

// 이 스크립트에선 403/400이 "기대값"이므로 http_req_failed로 세지 않게 한다
http.setResponseCallback(http.expectedStatuses({ min: 100, max: 599 }));

export const options = Object.assign({}, commonOptions, {
  scenarios: { security: { executor: "per-vu-iterations", vus: 1, iterations: 1 } },
  thresholds: { checks: ["rate>=0"] },
});

const denied = (r) => r.status === 403 || r.status === 401 || /\/login|denied|403/.test(location(r));
const STACK_TRACE = /(\tat |at com\.dsa\.|at org\.springframework|\.java:\d+\)|Exception:|"trace"\s*:)/;
const LEAK = new RegExp(`(${PASSWORD.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}|hibernateLazyInitializer|\\$2[aby]\\$\\d\\d\\$|"password"\\s*:)`);

function post(path, form, csrf, name) {
  return http.post(`${BASE_URL}${path}`, withCsrf(form, csrf), aux(name || `POST ${path}`, { redirects: 0 }));
}
function get(path, name, extra = {}) {
  return http.get(`${BASE_URL}${path}`, aux(name || `GET ${path}`, Object.assign({ redirects: 0 }, extra)));
}
function asUser(user) {
  clearCookies();
  const r = login(user);
  if (!r.ok) throw new Error(`${user} 로그인 실패`);
  return csrfFrom(http.get(`${BASE_URL}/login`, aux("GET /login (csrf)")).body);
}

export default function () {
  group("REQ-NF-G-001 평문 통신", () => {
    check(null, { "BASE_URL이 https": () => BASE_URL.startsWith("https://") });
    if (HTTP_URL) {
      let r;
      try {
        r = http.get(`${HTTP_URL.replace(/\/$/, "")}/login`, aux("GET http /login", { redirects: 0, timeout: "5s" }));
      } catch (e) {
        r = { status: 0, headers: {} };
      }
      check(r, { "평문 포트는 https로 리다이렉트되거나 닫혀 있음": (x) => x.status === 0 || (x.status >= 300 && x.status < 400 && /^https:/.test(location(x))) });
    }
  });

  group("REQ-NF-G-003 / A-002 비로그인 접근 차단", () => {
    clearCookies();
    for (const p of ["/attendance", "/hr/employees", "/hr/positions", "/hr/departments", "/inventory", "/inventory/items", "/inventory/outbound"]) {
      const r = get(p);
      check(r, { [`비로그인 ${p} → /login`]: (x) => x.status >= 300 && x.status < 400 && /\/login/.test(location(x)) });
    }
  });

  // 일반 사원(EMPLOYEE) 세션
  const empCsrf = asUser(USERS.emp);

  group("REQ-NF-H-004 IDOR (일반 사원 → 타 사원 정보 변경)", () => {
    check(get(`/hr/employees/${TARGET_EMP_ID}/info`), { "일반 사원 info 조회 403": denied });
    // name을 비워 보내서, 인가가 없어도 검증(400)에서 끝나게 함 → 데이터 변화 없음
    const r = post(`/hr/employees/${TARGET_EMP_ID}/assignment`, { name: "", email: "", phone: "", address: "", positionId: "", departmentId: "" }, empCsrf);
    check(r, { "일반 사원 assignment 변경 403": denied });
  });

  group("REQ-NF-H-005 직책/부서 삭제 인가", () => {
    // 1번 직책/부서는 소속 사원이 있어 삭제가 차단됨 → 인가가 없어도 데이터 변화 없음
    check(post("/hr/positions/1/delete", {}, empCsrf), { "일반 사원 직책 삭제 403": denied });
    check(post("/hr/departments/1/delete", {}, empCsrf), { "일반 사원 부서 삭제 403": denied });
  });

  group("REQ-NF-I-002 재고/품목 관리 인가", () => {
    check(post("/inventory/outbound", { itemId: "", quantity: "0" }, empCsrf), { "일반 사원 출고 403": denied });
    check(post("/inventory/inbound", { itemId: "", quantity: "0" }, empCsrf), { "일반 사원 입고 403": denied });
    check(post("/inventory", { name: "" }, empCsrf), { "일반 사원 품목 등록 403": denied });
    check(post(`/inventory/${ITEM_ID}`, { name: "" }, empCsrf), { "일반 사원 품목 수정 403": denied });
  });

  if (ALLOW_WRITES) {
    group("REQ-NF-M-001 권한 상승 (일반 사원이 role=ADMIN_HR로 회원 등록)", () => {
      const u = `k6sec${Date.now() % 1e8}`;
      const r = post(
        "/admin/members",
        { username: u, password: "Passw0rd!k6", name: "k6보안", email: `${u}@k6.test`, phone: "010-0000-0000", address: "-", departmentId: "1", positionId: "1", role: "ADMIN_HR" },
        empCsrf
      );
      check(r, { "일반 사원 관리자 계정 생성 차단(403)": denied });
      if (!denied(r)) console.warn(`[M-001] 차단되지 않음 → 계정 ${u} 가 생성됐을 수 있음. DB에서 확인/삭제 필요`);
    });
  }

  // 인사관리자 세션
  const hrCsrf = asUser(USERS.hr);

  group("REQ-NF-G-004 예외 응답에 스택트레이스 노출", () => {
    const r1 = get(`/hr/employees/99999999/info`);
    check(r1, {
      "존재하지 않는 사원 → 5xx 아님": (r) => r.status < 500,
      "존재하지 않는 사원 → 스택트레이스 없음": (r) => !STACK_TRACE.test(r.body || ""),
    });
    const r2 = get(`/inventory/items?page=abc`);
    check(r2, {
      "잘못된 파라미터 타입 → 5xx 아님": (r) => r.status < 500,
      "잘못된 파라미터 타입 → 스택트레이스 없음": (r) => !STACK_TRACE.test(r.body || ""),
    });
  });

  group("REQ-NF-A-001 / H-001 / I-001 / G-002 민감 필드 노출", () => {
    for (const p of ["/hr/employees", `/hr/employees/${TARGET_EMP_ID}/info`, "/inventory/items", "/inventory/outbound", "/hr/positions"]) {
      const r = get(p, `GET ${p} (leak)`, { redirects: 5 });
      check(r, { [`${p} 응답에 비밀번호/해시/프록시 필드 없음`]: (x) => !LEAK.test(x.body || "") });
    }
  });

  if (ALLOW_WRITES) {
    group("REQ-NF-H-002 서버단 입력 검증", () => {
      check(post("/hr/positions", { positionName: "" }, hrCsrf), { "빈 직책명 → 400 또는 폼 재표시(저장 안 됨)": (r) => r.status === 400 || r.status === 200 });
      check(post("/hr/positions", { positionName: "사원" }, hrCsrf), { "중복 직책명 → 400 또는 폼 재표시": (r) => r.status === 400 || r.status === 200 });
      check(post("/hr/departments", { departmentName: "" }, hrCsrf), { "빈 부서명 → 400 또는 폼 재표시": (r) => r.status === 400 || r.status === 200 });
      check(post("/hr/departments", { departmentName: "인사팀" }, hrCsrf), { "중복 부서명 → 400 또는 폼 재표시": (r) => r.status === 400 || r.status === 200 });
      console.warn("[H-002] 결함 상태라면 빈 이름/중복 직책·부서가 생성됐을 수 있음 → 목록에서 확인 후 비활성화");
    });
  }

  group("REQ-NF-L-002 로그인 실패 메시지로 계정 존재 여부 노출", () => {
    clearCookies();
    const norm = (b) => (b || "").replace(/value="[^"]*"/g, "").replace(/content="[^"]*"/g, "");
    const fail = (u) => {
      const page = http.get(`${BASE_URL}/login`, aux("GET /login"));
      return http.post(`${BASE_URL}/login`, withCsrf({ username: u, password: "wrong-password-1" }, csrfFrom(page.body)), aux("POST /login (fail)"));
    };
    const a = fail(USERS.emp);
    const b = fail("no_such_user_k6");
    check(null, {
      "존재/비존재 ID 실패 응답 상태 동일": () => a.status === b.status,
      "존재/비존재 ID 실패 화면 동일": () => norm(a.body) === norm(b.body),
    });
  });

  group("REQ-NF-L-004 로그아웃 후 세션 무효화", () => {
    clearCookies();
    login(USERS.emp);
    const sid = http.cookieJar().cookiesForURL(BASE_URL)["JSESSIONID"][0];
    const csrf = csrfFrom(http.get(`${BASE_URL}/login`, aux("GET /login")).body);
    post("/logout", {}, csrf, "POST /logout");
    clearCookies();
    const r = http.get(`${BASE_URL}/attendance`, aux("GET /attendance (old session)", { redirects: 0, cookies: { JSESSIONID: sid } }));
    check(r, { "로그아웃 전 세션ID로 보호 페이지 접근 → /login": (x) => x.status >= 300 && x.status < 400 && /\/login/.test(location(x)) });
  });

  group("REQ-NF-L-003 세션 정책 (정보 출력용)", () => {
    const jarA = new http.CookieJar();
    const jarB = new http.CookieJar();
    login(USERS.emp, PASSWORD, jarA);
    login(USERS.emp, PASSWORD, jarB);
    const r = http.get(`${BASE_URL}/attendance`, aux("GET /attendance (session A)", { jar: jarA, redirects: 0 }));
    const aAlive = r.status === 200;
    console.log(`[L-003] 같은 계정 2중 로그인 후 첫 세션 유효 여부: ${aAlive ? "유효(동시 로그인 허용)" : "만료(동시 로그인 제한)"}`);
    check(null, { [`동시 로그인 정책이 기대값(${__ENV.EXPECT_CONCURRENT || "block"})과 일치`]: () => (__ENV.EXPECT_CONCURRENT === "allow" ? aAlive : !aAlive) });

    const wait = Number(__ENV.SESSION_WAIT_S || 0); // 예: 세션 타임아웃을 1m으로 줄인 뒤 SESSION_WAIT_S=70
    if (wait > 0) {
      sleep(wait);
      const e = http.get(`${BASE_URL}/attendance`, aux("GET /attendance (after idle)", { jar: jarB, redirects: 0 }));
      check(e, { [`${wait}s 미사용 후 세션 만료 → /login`]: (x) => /\/login/.test(location(x)) });
    }
  });
}

export const handleSummary = makeSummary("SECURITY", "보안 결함 기능 검증 (✓ 개선됨 / ✗ 결함 존재)", "functional");
