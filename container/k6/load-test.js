import http from "k6/http";
import { check, sleep } from "k6";

// ─────────────────────────────────────────────────────────────
// 매트릭스의 "측정 방법론 표준"에 맞춘 k6 스크립트 템플릿
//   지표: TPS, 응답시간 p50/p95/p99, 에러율(%), 5xx 발생 시점 동시사용자 수
//   장애 정의: 에러율 5% 초과 또는 p95 응답시간 2초 초과
//
// 실행 예:
//   BASE_URL=http://localhost:8080 TEST_USER=... TEST_PASS=... k6 run k6/load-test.js
// ─────────────────────────────────────────────────────────────

export const options = {
  stages: [
    { duration: "30s", target: 20 },   // 워밍업
    { duration: "1m", target: 50 },    // 평상시 부하 가정
    { duration: "1m", target: 100 },   // 피크 부하 가정
    { duration: "30s", target: 0 },    // 쿨다운
  ],
  thresholds: {
    http_req_duration: ["p(95)<2000"], // 매트릭스 기준: p95 2초
    http_req_failed: ["rate<0.05"],     // 매트릭스 기준: 에러율 5%
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// 세션 기반 로그인이므로, 테스트 시작 시 1회만 로그인해서 쿠키를 얻고
// 각 VU가 그 쿠키를 재사용하도록 구성 (매 iteration마다 로그인하면
// 로그인 처리 자체의 지연이 측정치에 섞여 들어감)
export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/login`,
    {
      username: __ENV.TEST_USER || "testuser",
      password: __ENV.TEST_PASS || "testpass",
    },
    { redirects: 0 }
  );

  const jsessionCookie = loginRes.cookies["JSESSIONID"];
  if (!jsessionCookie || jsessionCookie.length === 0) {
    throw new Error(
      `로그인 실패 (status=${loginRes.status}) - JSESSIONID를 못 받아옴. ` +
        `TEST_USER/TEST_PASS 환경변수와 계정 상태를 확인하세요.`
    );
  }

  return { sessionCookie: jsessionCookie[0].value };
}

export default function (data) {
  // VU별 쿠키 jar에 세션 쿠키를 세팅
  const jar = http.cookieJar();
  jar.set(BASE_URL, "JSESSIONID", data.sessionCookie);

  // TODO: 실제 측정 대상 엔드포인트로 교체
  // 예: F1(본인 근태 조회, N+1) / F9(재고 조회, N+1) 등 매트릭스의 대상 기능
  const res = http.get(`${BASE_URL}/api/attendance/my?month=2026-09`);

  check(res, {
    "status is 200": (r) => r.status === 200,
    "not 5xx": (r) => r.status < 500,
  });

  sleep(1);
}
