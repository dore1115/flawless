// ─────────────────────────────────────────────────────────────
// 공통 유틸: 로그인(CSRF 포함), 세션 관리, HTML 파싱 헬퍼
//
// 이 앱은 Thymeleaf SSR + Spring Security formLogin 구조라서
//   1) POST 요청마다 _csrf 토큰이 필요하고 (SecurityConfig에서 csrf를 끄지 않음)
//   2) 성공/실패가 상태코드가 아니라 302 Location으로 구분된다.
// ─────────────────────────────────────────────────────────────
import http from "k6/http";
import { parseHTML } from "k6/html";
import { Counter } from "k6/metrics";
import { sleep } from "k6";
import exec from "k6/execution";

export const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
export const PASSWORD = __ENV.TEST_PASS || "password123!";

// ── 계정 구성 (브랜치마다 seed_data.sql이 다름) ─────────────────────
//   seed(dev)            : emp001~003 ADMIN_HR / emp004~006 ADMIN_IV / emp007~100 EMPLOYEE, 비밀번호 평문
//   fix(fix-REQ-NF-G~)   : emp001~020 ADMIN_HR / emp021~040 ADMIN_IV / emp041~100 EMPLOYEE, BCrypt, max-sessions=1
// ACCOUNTS=seed|fix 로 고르고, 지정하지 않으면 LABEL이 seed면 seed, 그 외(fix/alt/final)는 fix.
// HR_USER / IV_USER / EMP_USER / EMP_FIRST / ID_FROM 을 직접 주면 그 값이 우선한다.
const ACCOUNT_PRESETS = {
  seed: { hr: "emp001", iv: "emp004", emp: "emp007", empFirst: 7, idFrom: 7, loginMode: "shared" },
  fix: { hr: "emp001", iv: "emp021", emp: "emp041", empFirst: 41, idFrom: 21, loginMode: "multi" },
};
export const ACCOUNTS = (__ENV.ACCOUNTS || ((__ENV.LABEL || "seed") === "seed" ? "seed" : "fix")).toLowerCase();
if (!ACCOUNT_PRESETS[ACCOUNTS]) throw new Error(`알 수 없는 ACCOUNTS: ${ACCOUNTS} (seed|fix)`);
export const ACCOUNT = ACCOUNT_PRESETS[ACCOUNTS];

// 관리자 세션 여러 개를 만드는 방식 (H-007, H-008)
//   shared: 한 계정으로 여러 번 로그인 (seed — 관리자 계정이 3개뿐, 동시 로그인 제한 없음)
//   multi : 계정을 바꿔가며 하나씩 로그인 (fix — max-sessions=1이라 같은 계정 재로그인 시 이전 세션 만료)
export const LOGIN_MODE = (__ENV.LOGIN_MODE || ACCOUNT.loginMode).toLowerCase();

export const USERS = {
  hr: __ENV.HR_USER || ACCOUNT.hr,
  iv: __ENV.IV_USER || ACCOUNT.iv,
  emp: __ENV.EMP_USER || ACCOUNT.emp,
};

export const loginFailures = new Counter("nfr_login_failures");

/** EMPLOYEE 계정 풀에서 n번째 계정 (VU마다 다른 사원으로 조회하도록) */
export function employeeUserFor(n) {
  const first = Number(__ENV.EMP_FIRST || ACCOUNT.empFirst);
  const last = Number(__ENV.EMP_LAST || 100);
  const idx = first + (Math.abs(n) % (last - first + 1));
  return "emp" + String(idx).padStart(3, "0");
}

/** 측정 대상 요청용 params. kind:target 태그가 붙은 요청만 threshold/summary 집계 대상 */
export function target(name, extra = {}) {
  return Object.assign({ tags: { name, kind: "target" } }, extra);
}

/** 측정 대상이 아닌 보조 요청(로그인, CSRF 토큰 획득 등) */
export function aux(name, extra = {}) {
  return Object.assign({ tags: { name, kind: "aux" } }, extra);
}

export function csrfFrom(body) {
  if (!body) return null;
  const m =
    body.match(/name="_csrf"[^>]*?value="([^"]+)"/) ||
    body.match(/value="([^"]+)"[^>]*?name="_csrf"/) ||
    body.match(/<meta\s+name="_csrf"\s+content="([^"]+)"/);
  return m ? m[1] : null;
}

/** form body에 _csrf를 붙여준다 (CSRF가 꺼진 브랜치면 그냥 통과) */
export function withCsrf(form, token) {
  return token ? Object.assign({ _csrf: token }, form) : form;
}

export function location(res) {
  return res.headers["Location"] || res.headers["location"] || "";
}

/**
 * redirects:0 으로 측정한 POST 뒤에, 브라우저처럼 Location을 한 번 GET 한다 (측정 대상 아님).
 * Spring의 addFlashAttribute()는 리다이렉트 후 GET에서 꺼내져야 세션에서 지워진다.
 * 따라가지 않으면 플래시 데이터가 세션에 계속 쌓여 힙을 채운다 (기본 180초 후 만료, 정리는 다음 요청 시).
 * FOLLOW_REDIRECT=false 로 끌 수 있다.
 */
export function followRedirect(res, name = "GET (redirect follow)") {
  if ((__ENV.FOLLOW_REDIRECT || "true") === "false") return null;
  const loc = location(res);
  if (!loc || res.status < 300 || res.status >= 400) return null;
  const url = /^https?:\/\//.test(loc) ? loc : `${BASE_URL}${loc.startsWith("/") ? "" : "/"}${loc}`;
  return http.get(url, aux(name, { redirects: 0 }));
}

export function redirectedToLogin(res) {
  return /\/login(\?|$|#)/.test(location(res)) || /\/login(\?|$|#)/.test(res.url || "");
}

/** 최종 URL의 path가 기대한 경로인지 (세션이 풀려 / 나 /login으로 튕긴 경우를 잡기 위함) */
export function landedOn(res, pathPrefix) {
  const path = (res.url || "").replace(/^https?:\/\/[^/]+/, "").split("?")[0];
  return path.startsWith(pathPrefix);
}

/**
 * 폼 로그인. jar를 넘기면 그 jar로, 아니면 VU 기본 jar로 로그인한다.
 * 성공 판정: 302 이고 Location이 /login 이 아닐 것 (failureUrl = /login)
 */
export function login(username, password = PASSWORD, jar = undefined) {
  const j = jar ? { jar } : {};
  const page = http.get(`${BASE_URL}/login`, aux("GET /login", j));
  const res = http.post(
    `${BASE_URL}/login`,
    withCsrf({ username, password }, csrfFrom(page.body)),
    aux("POST /login", Object.assign({ redirects: 0 }, j))
  );
  const ok = res.status >= 300 && res.status < 400 && !/\/login(\?|$|#)/.test(location(res));
  return { ok, res };
}

// ── VU별 세션 (VU마다 다른 계정으로 로그인해 두고 재사용) ──────────
let vuSession = null;
let loggedFailure = false;

/** 로그인 실패 원인을 사람이 읽을 수 있게 */
export function describeLoginFailure(res) {
  const loc = location(res);
  if (res.status === 0) {
    return `서버 연결 실패 (${res.error || "no response"}) → BASE_URL=${BASE_URL} 확인. ` +
      `k6를 도커로 돌리면 localhost는 k6 컨테이너 자신이라 BASE_URL=http://app:8080 + --network 필요`;
  }
  if (res.status === 403) return `403 → CSRF 토큰 불일치/누락 (GET /login 응답에 _csrf가 있는지 확인)`;
  if (/\/login/.test(loc)) {
    return `302 → ${loc} (인증 실패) → DB에 해당 계정/비밀번호가 있는지 확인. ` +
      `mysql-init은 볼륨 최초 생성 시에만 실행되므로 볼륨이 예전 것이면 시드가 안 들어가 있을 수 있음. ` +
      `fix 브랜치(BCrypt 적용)라면 DB 비밀번호도 해시로 바뀌어 있어야 함`;
  }
  return `status=${res.status}, Location=${loc || "-"}`;
}

export function ensureSession(username) {
  if (vuSession === username) return true;
  clearCookies();
  const r = login(username);
  if (!r.ok) {
    loginFailures.add(1);
    if (!loggedFailure) {
      loggedFailure = true; // VU당 1회만 원인 출력
      console.warn(`[login] ${username} 실패: ${describeLoginFailure(r.res)}`);
    }
    vuSession = null;
    sleep(1); // 서버가 죽었을 때 로그인 재시도가 폭주하지 않도록
    return false;
  }
  vuSession = username;
  return true;
}

export function resetSession() {
  vuSession = null;
}

export function clearCookies(jar = http.cookieJar()) {
  try {
    jar.clear(BASE_URL);
  } catch (e) {
    jar.delete(BASE_URL, "JSESSIONID");
  }
}

// ── setup()에서 로그인한 세션을 VU들이 나눠 쓴다 ─────────────────────
// 관리자 전용 기능은 setup에서 만든 세션으로 부하를 건다.
// count > 1 이면 같은 계정으로 세션을 여러 개 만들어 VU에 나눠준다.
//   → 세션 1개를 수백 VU가 공유하면 Spring의 세션 mutex(flash map 저장 등)에서 요청이 줄을 서서,
//     실제 사용 환경에 없는 병목이 생긴다.
// (REQ-NF-L-003 수정으로 동시 로그인 수가 제한되면 count를 그 이하로 둘 것)
export function sharedLogin(username, csrfPage = "/login", count = 1) {
  const sessions = [];
  for (let i = 0; i < count; i++) {
    clearCookies();
    const r = login(username);
    if (!r.ok) throw new Error(`[setup] ${username} 로그인 실패(${i + 1}/${count}): ${describeLoginFailure(r.res)}`);
    const sid = http.cookieJar().cookiesForURL(BASE_URL)["JSESSIONID"];
    if (!sid || !sid.length) throw new Error("[setup] JSESSIONID 쿠키를 받지 못했습니다.");
    const page = http.get(`${BASE_URL}${csrfPage}`, aux(`GET ${csrfPage}`));
    sessions.push({ sid: sid[0], csrf: csrfFrom(page.body) });
  }
  if (count > 1) console.log(`[setup] ${username} 세션 ${count}개 생성`);
  return { sid: sessions[0].sid, csrf: sessions[0].csrf, sessions };
}

// ── 같은 역할의 서로 다른 계정 count개로 하나씩 로그인해 세션을 만든다 (fix-REQ-NF-G cc191b9에서 가져옴) ──
// max-sessions=1(REQ-NF-S-001)이라 sharedLogin처럼 한 계정으로 여러 번 로그인하면 이전 세션이 만료된다.
// firstUser부터 번호를 올려가며 로그인한다. 예) multiLogin("emp001", ..., 20) → emp001~emp020 (ADMIN_HR)
// 반환 형태는 sharedLogin과 같아서 useSharedSession(data)로 VU에 나눠준다.
export function multiLogin(firstUser, csrfPage = "/login", count = 1) {
  const m = firstUser.match(/^(.*?)(\d+)$/);
  if (!m) throw new Error(`[setup] 계정 번호를 알 수 없습니다: ${firstUser}`);
  const [, prefix, num] = m;
  const sessions = [];
  for (let i = 0; i < count; i++) {
    const username = prefix + String(Number(num) + i).padStart(num.length, "0");
    clearCookies();
    const r = login(username);
    if (!r.ok) throw new Error(`[setup] ${username} 로그인 실패(${i + 1}/${count}): ${describeLoginFailure(r.res)}`);
    const sid = http.cookieJar().cookiesForURL(BASE_URL)["JSESSIONID"];
    if (!sid || !sid.length) throw new Error(`[setup] ${username} JSESSIONID 쿠키를 받지 못했습니다.`);
    const page = http.get(`${BASE_URL}${csrfPage}`, aux(`GET ${csrfPage}`));
    sessions.push({ sid: sid[0], csrf: csrfFrom(page.body) });
  }
  console.log(`[setup] ${firstUser}부터 계정 ${count}개로 세션 ${count}개 생성`);
  return { sid: sessions[0].sid, csrf: sessions[0].csrf, sessions };
}

/** 관리자 세션 count개 생성 — LOGIN_MODE(shared|multi)에 따라 sharedLogin/multiLogin 선택 */
export function adminLogin(firstUser, csrfPage = "/login", count = 1) {
  console.log(`[setup] ACCOUNTS=${ACCOUNTS}, LOGIN_MODE=${LOGIN_MODE}, 관리자 세션 ${count}개`);
  return LOGIN_MODE === "multi" ? multiLogin(firstUser, csrfPage, count) : sharedLogin(firstUser, csrfPage, count);
}

/** VU 번호로 세션 하나를 골라 쿠키에 넣고, 그 세션({sid, csrf})을 돌려준다 */
export function useSharedSession(data) {
  const list = data.sessions && data.sessions.length ? data.sessions : [data];
  let vu = 0;
  try {
    vu = exec.vu.idInTest || 0; // setup/teardown에서는 0
  } catch (e) {}
  const s = list[vu % list.length];
  http.cookieJar().set(BASE_URL, "JSESSIONID", s.sid);
  return s;
}

// ── HTML 파싱 헬퍼 ─────────────────────────────────────────
export function doc(res) {
  return parseHTML(res.body || "");
}

export function inputValue(d, selector) {
  const v = d.find(selector).first().attr("value");
  return v === undefined ? "" : v;
}

export function selectedValue(d, selectId) {
  const sel = d.find(`#${selectId} option[selected]`).first().attr("value");
  return sel === undefined ? "" : sel;
}

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}
