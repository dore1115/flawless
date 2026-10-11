// REQ-NF-H-007 (사원 조회 N+1)
// 대상: GET  /hr/employees                  (MODE=list, 기본값) 사원 목록
//       GET  /hr/employees/{id}/info        (MODE=update|both) 변경 대상 사원 조회 + 직책/부서 옵션
//       POST /hr/employees/{id}/assignment  (MODE=update|both) 변경 처리
//
// 결함 위치: EmployeeAssignmentService.findAll() → employeeRepository.findAll()
//   Employee.department/position 이 EAGER라 목록 조회 후 부서·직책을 건마다 추가 SELECT (N+1).
//   개선: join fetch 쿼리 (EmployeeRepository 주석 참고).
//   단건 조회(info)는 EAGER가 조인으로 처리돼 N+1이 없다 → MODE=update로는 결함이 안 보임.
//
// 비교 방법: 응답시간이 seed/fix 모두 수 ms라 breakpoint로는 안 터진다(200 req/s까지 확인).
//   PROFILE=load로 같은 RATE를 주고, 결과의 nfr_server_cpu_ms_per_req(요청당 서버 CPU)를 비교한다.
//   (lib/server-metrics.js — Prometheus PROM_URL에서 읽음)
//
// 변경값은 "현재 값 그대로" 다시 전송한다 → 데이터는 바뀌지 않고, 조회/검증 경로의 쿼리 수만 측정됨.
// 관리자 세션 SHARED_SESSIONS(기본 20)개를 setup에서 만들어 VU들이 나눠 쓴다.
//   seed: emp001 한 계정으로 20번 로그인(shared) / fix: emp001~020 각각 로그인(multi, max-sessions=1 대응)
import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, USERS, ACCOUNT, adminLogin, useSharedSession, target, doc, inputValue, selectedValue, csrfFrom, withCsrf, location, landedOn, followRedirect } from "../lib/common.js";
import { PROFILE, commonOptions, scenarioFor, thresholdsFor, tagStage, makeSummary } from "../lib/profiles.js";
import { serverSnapshot, recordServerDelta } from "../lib/server-metrics.js";

const MODE = __ENV.MODE || "list"; // list | update | both
const ID_FROM = Number(__ENV.ID_FROM || ACCOUNT.idFrom); // seed 7 / fix 21
const ID_TO = Number(__ENV.ID_TO || 100);

export const options = Object.assign({}, commonOptions, {
  scenarios: { employee: scenarioFor("default", { RATE: 50 }) },
  thresholds: thresholdsFor(["GET /hr/employees/{id}/info", "POST /hr/employees/{id}/assignment", "GET /hr/employees"]),
});

export function setup() {
  const session = adminLogin(USERS.hr, "/hr/employees", Number(__ENV.SHARED_SESSIONS || 20));
  return Object.assign(session, { prom: serverSnapshot() });
}

export function teardown(data) {
  recordServerDelta(data.prom);
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

export const handleSummary = makeSummary("REQ-NF-H-007", `사원 조회 N+1 (MODE=${MODE})`);
