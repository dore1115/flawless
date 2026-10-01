import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

// ─────────────────────────────────────────────────────────────
// REQ-NF-L-005 로그인 응답 지연 측정용
//   기준: 동시 사용자 100명, 평균 500ms 초과 또는 p95 1초 초과 시 결함
//   login_duration: 로그인 요청(POST /login)만 따로 잰 응답시간
// 실행 예:
//   k6 run container/k6/login-load-test.js
// ─────────────────────────────────────────────────────────────

const loginDuration = new Trend("login_duration", true);

export const options = {
  stages: [
    { duration: "30s", target: 20 },
    { duration: "1m", target: 50 },
    { duration: "1m", target: 100 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    login_duration: ["avg<500", "p(95)<1000"],
    checks: ["rate>0.95"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export default function () {
  // 가상 사용자마다 다른 계정 사용 (emp001 ~ emp100)
  const username = `emp${String(((__VU - 1) % 100) + 1).padStart(3, "0")}`;

  // 1) 로그인 페이지 GET → CSRF 토큰 + 세션 쿠키 받기
  const loginPage = http.get(`${BASE_URL}/login`);
  const csrfMatch = loginPage.body.match(/name="_csrf" value="([^"]+)"/);
  const csrfToken = csrfMatch ? csrfMatch[1] : null;

  if (!csrfToken) {
    console.error("CSRF 토큰을 못 찾음 - 로그인 페이지 HTML 구조 확인 필요");
    sleep(1);
    return;
  }

  // 2) 로그인 요청
  const res = http.post(
      `${BASE_URL}/login`,
      {
        username: username,
        password: "password123!",
        _csrf: csrfToken,
      },
      { redirects: 0 }
  );

  loginDuration.add(res.timings.duration);

  // 3) 로그인 "성공" 확인: 302이면서 로그인 페이지로 돌아가지 않아야 함
  const location = res.headers["Location"] || "";

  // [확인용] 각 사용자의 첫 로그인 결과를 화면에 출력
  if (__ITER === 0) console.log(`${username} → ${res.status} ${location}`);

  check(res, {
    "로그인 성공 (302, /login으로 안 돌아감)": (r) =>
        r.status === 302 && !location.includes("/login"),
  });

  sleep(1);
}