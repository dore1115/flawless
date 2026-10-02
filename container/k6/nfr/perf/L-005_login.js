// REQ-NF-L-005 (로그인 응답 지연)
// 요구사항 기준: 동시 사용자 100명 로그인 시 평균 500ms 초과 또는 p95 1초 초과 여부
//   → 기본 PROFILE=vus, VUS=100, DURATION=2m (정의서의 JMeter 측정을 k6로 대체)
//
// 매 iteration: 쿠키 초기화 → GET /login(CSRF) → POST /login(측정 대상) → 로그아웃
// 로그아웃을 안 하면 세션이 계속 쌓여 1GB 컨테이너의 힙을 잡아먹고, 그게 결과를 오염시킨다.
// (세션 누적 자체를 보고 싶으면 LOGOUT=false)
import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import { BASE_URL, PASSWORD, employeeUserFor, clearCookies, csrfFrom, withCsrf, location, target, aux } from "../lib/common.js";
import { PROFILE, commonOptions, scenarioFor, thresholdsFor, tagStage, makeSummary } from "../lib/profiles.js";

const LOGOUT = (__ENV.LOGOUT || "true") === "true";

export const options = Object.assign({}, commonOptions, {
  scenarios: { login: scenarioFor("default", { VUS: 100, DURATION: "2m", RATE: 20 }) },
  thresholds: thresholdsFor([], {
    // 요구사항정의서 판정 기준 그대로
    "http_req_duration{kind:target}": ["avg<500", "p(95)<1000", `p(99)<${__ENV.P99_LIMIT_MS || 3000}`],
  }),
});

export default function () {
  tagStage();
  clearCookies();
  const user = employeeUserFor(exec.scenario.iterationInTest);

  const page = http.get(`${BASE_URL}/login`, aux("GET /login"));
  const res = http.post(
    `${BASE_URL}/login`,
    withCsrf({ username: user, password: PASSWORD }, csrfFrom(page.body)),
    target("POST /login", { redirects: 0 })
  );
  const ok = check(res, {
    "로그인 성공(302, /login 아님)": (r) => r.status === 302 && !/\/login(\?|$)/.test(location(r)),
  });

  if (ok && LOGOUT) {
    // 로그인 후 세션 고정 방지로 CSRF 토큰이 재발급되므로 새 토큰을 받아서 로그아웃
    const p = http.get(`${BASE_URL}/login`, aux("GET /login (csrf)"));
    http.post(`${BASE_URL}/logout`, withCsrf({}, csrfFrom(p.body)), aux("POST /logout", { redirects: 0 }));
  }
  if (PROFILE === "vus" || PROFILE === "smoke") sleep(Number(__ENV.THINK_S || 1));
}

export const handleSummary = makeSummary("REQ-NF-L-005", "로그인 응답 지연");
