import http from "k6/http";
import { check, sleep } from "k6";

// ─────────────────────────────────────────────────────────────
// REQ-NF-L-005 로그인 응답 지연 측정용
//   기준: 동시 사용자 100명, 평균 500ms 초과 또는 p95 1초 초과 시 결함
// 실행 예:
//   BASE_URL=http://localhost:8080 k6 run k6/login-load-test.js
// ─────────────────────────────────────────────────────────────

export const options = {
  stages: [
    { duration: "30s", target: 20 },
    { duration: "1m", target: 50 },
    { duration: "1m", target: 100 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(95)<1000"],
    http_req_failed: ["rate<0.05"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export default function () {
  // 1) 로그인 페이지 먼저 GET → CSRF 토큰 + 세션 쿠키 받기
  const loginPage = http.get(`${BASE_URL}/login`);
  const csrfMatch = loginPage.body.match(/name="_csrf" value="([^"]+)"/);
  const csrfToken = csrfMatch ? csrfMatch[1] : null;

  if (!csrfToken) {
    console.error("CSRF 토큰을 못 찾음 - 로그인 페이지 HTML 구조 확인 필요");
    sleep(1);
    return;
  }

  // 2) 받은 토큰과 함께 로그인 요청
  const res = http.post(
      `${BASE_URL}/login`,
      {
        username: "emp001",
        password: "password123!",
        _csrf: csrfToken,
      },
      { redirects: 0 }
  );

  check(res, {
    "status is 302 or 200": (r) => r.status === 302 || r.status === 200,
  });

  sleep(1);
}