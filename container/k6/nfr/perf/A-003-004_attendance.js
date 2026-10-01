// REQ-NF-A-003 (N+1) / REQ-NF-A-004 (전체 기간 조회 후 메모리 필터링)
// 대상: GET /attendance?year=&month=  (본인 월별 근태 조회)
//
// 결함: AttendanceService.getList()가 findAllByEmployeeUsername()으로 해당 사원의
//       "전체 기간" 근태를 다 읽은 뒤 자바에서 월 필터링 + Attendance.employee EAGER
// 개선: 기간 조건 쿼리 + fetch join
//
// 관측 포인트: 사원 1명당 근태 행 수가 늘수록(개선 전) 응답시간이 선형 증가해야 정상적으로 재현된 것.
//   시드는 1인당 약 1년(≈260행)이라 차이가 작다 → data/z-scale-up.sql 로 5년치로 늘린 뒤 측정 권장.
import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import { BASE_URL, employeeUserFor, ensureSession, resetSession, target, landedOn } from "../lib/common.js";
import { PROFILE, commonOptions, scenarioFor, thresholdsFor, tagStage, makeSummary } from "../lib/profiles.js";

export const options = Object.assign({}, commonOptions, {
  scenarios: { attendance: scenarioFor("default", { RATE: 20 }) },
  thresholds: thresholdsFor(),
});

// 조회 월 범위 (기본: 시드 데이터 범위)
const FROM = (__ENV.FROM_YM || "2025-09").split("-").map(Number);
const TO = (__ENV.TO_YM || "2026-09").split("-").map(Number);
const MONTHS = [];
for (let y = FROM[0], m = FROM[1]; y < TO[0] || (y === TO[0] && m <= TO[1]); m === 12 ? (y++, (m = 1)) : m++) MONTHS.push([y, m]);

export default function () {
  tagStage();
  if (!ensureSession(employeeUserFor(exec.vu.idInTest))) return;

  const [y, m] = MONTHS[Math.floor(Math.random() * MONTHS.length)];
  const res = http.get(`${BASE_URL}/attendance?year=${y}&month=${m}`, target("GET /attendance"));

  const ok = check(res, {
    "status 200": (r) => r.status === 200,
    "근태 화면 도착(세션 유지)": (r) => landedOn(r, "/attendance"),
  });
  if (!ok && !landedOn(res, "/attendance")) resetSession();

  if (PROFILE === "smoke" || PROFILE === "vus") sleep(Number(__ENV.THINK_S || 1));
}

export const handleSummary = makeSummary("REQ-NF-A-003-004", "본인 월별 근태 조회");
