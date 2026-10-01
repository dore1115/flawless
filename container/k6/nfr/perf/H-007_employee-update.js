// REQ-NF-H-007 (사원 정보 단일 변경 처리 N+1)
// 대상: GET  /hr/employees/{id}/info        (변경 대상 사원 조회 + 직책/부서 옵션)
//       POST /hr/employees/{id}/assignment  (변경 처리)
//       GET  /hr/employees                  (MODE=list 또는 both 일 때, 사원 목록 EAGER N+1)
//
// 변경값은 "현재 값 그대로" 다시 전송한다 → 데이터는 바뀌지 않고, 조회/검증 경로의 쿼리 수만 측정됨.
// 관리자 계정이 3개뿐이라 setup에서 emp001로 한 번 로그인한 세션을 모든 VU가 공유한다.
import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, USERS, sharedLogin, useSharedSession, target, doc, inputValue, selectedValue, csrfFrom, withCsrf, location, landedOn, followRedirect } from "../lib/common.js";
import { PROFILE, commonOptions, scenarioFor, thresholdsFor, tagStage, makeSummary } from "../lib/profiles.js";

const MODE = __ENV.MODE || "update"; // update | list | both
const ID_FROM = Number(__ENV.ID_FROM || 7);
const ID_TO = Number(__ENV.ID_TO || 100);

export const options = Object.assign({}, commonOptions, {
  scenarios: { employee: scenarioFor("default", { RATE: 10 }) },
  thresholds: thresholdsFor(["GET /hr/employees/{id}/info", "POST /hr/employees/{id}/assignment", "GET /hr/employees"]),
});

export function setup() {
  return sharedLogin(USERS.hr, "/hr/employees", Number(__ENV.SHARED_SESSIONS || 20));
}

export default function (data) {
  tagStage();
  const session = useSharedSession(data);

  if (MODE === "list" || MODE === "both") {
    const list = http.get(`${BASE_URL}/hr/employees`, target("GET /hr/employees"));
    check(list, { "목록 200": (r) => r.status === 200 && landedOn(r, "/hr/employees") });
    if (MODE === "list") return think();
  }

  const id = ID_FROM + Math.floor(Math.random() * (ID_TO - ID_FROM + 1));
  const info = http.get(`${BASE_URL}/hr/employees/${id}/info`, target("GET /hr/employees/{id}/info"));
  if (!check(info, { "info 200": (r) => r.status === 200 && landedOn(r, `/hr/employees/${id}/info`) })) return think();

  const d = doc(info);
  const form = {
    name: inputValue(d, "#name"),
    email: inputValue(d, "#email"),
    phone: inputValue(d, "#phone"),
    address: inputValue(d, "#address"),
    positionId: selectedValue(d, "positionId"),
    departmentId: selectedValue(d, "departmentId"),
  };
  // 낙관적 락(REQ-NF-H-009 수정)으로 폼에 version hidden 필드가 생겼다면 같이 보냄
  const version = inputValue(d, 'input[name="version"]');
  if (version) form.version = version;

  const res = http.post(
    `${BASE_URL}/hr/employees/${id}/assignment`,
    withCsrf(form, csrfFrom(info.body) || session.csrf),
    target("POST /hr/employees/{id}/assignment", { redirects: 0 })
  );
  check(res, {
    "변경 처리 302 → info": (r) => r.status === 302 && location(r).includes(`/hr/employees/${id}/info`),
  });
  followRedirect(res, "GET /hr/employees/{id}/info (flash 소비)"); // 완료 메시지 flash 비움 (측정 제외)
  think();
}

function think() {
  if (PROFILE === "smoke" || PROFILE === "vus") sleep(Number(__ENV.THINK_S || 1));
}

export const handleSummary = makeSummary("REQ-NF-H-007", "사원 정보 단일 변경 처리");
