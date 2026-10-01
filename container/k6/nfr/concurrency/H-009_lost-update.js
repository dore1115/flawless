// REQ-NF-H-009 (직책/부서 단일 변경 — Lost Update)
// 대상: POST /hr/employees/{id}/assignment
//
// 시나리오: 인사관리자 여러 명이 "같은 시점에 연 수정 화면(같은 스냅샷)"을 기준으로 동시에 저장한다.
//   - 홀수 VU: 직책만 바꿈 (부서는 스냅샷 값)
//   - 짝수 VU: 부서만 바꿈 (직책은 스냅샷 값)
//   정상(낙관적 락 + 폼에 version 포함): 첫 저장 1건만 성공, 나머지는 충돌로 거절 → accepted == 1
//   결함(락 없음): 모두 성공 → 마지막 저장이 앞선 변경을 덮어씀 → accepted > 1 (accepted-1 건이 유실)
//
// 참고: 비관적 락만 적용하고 폼에 version을 싣지 않으면 "순서만 직렬화"될 뿐 이 시나리오의
//       유실은 막지 못한다. 그래서 스크립트는 폼에 version hidden 필드가 있으면 자동으로 함께 보낸다.
// 끝나면 스냅샷 값으로 원복한다.
import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { Counter } from "k6/metrics";
import { BASE_URL, USERS, sharedLogin, useSharedSession, target, aux, doc, inputValue, selectedValue, csrfFrom, withCsrf, location } from "../lib/common.js";
import { commonOptions, makeSummary } from "../lib/profiles.js";

const TARGET_ID = __ENV.TARGET_ID || "50";
const RACE_VUS = Number(__ENV.RACE_VUS || 10);

const accepted = new Counter("nfr_update_accepted");
const rejected = new Counter("nfr_update_rejected");

export const options = Object.assign({}, commonOptions, {
  scenarios: { race: { executor: "per-vu-iterations", vus: RACE_VUS, iterations: 1, maxDuration: "1m" } },
  thresholds: {
    "http_req_duration{kind:target}": ["max>=0"],
    "http_reqs{kind:target}": ["count>=0"],
    "http_req_failed{kind:target}": ["rate>=0"],
    nfr_update_accepted: ["count<=1"], // 판정: 스냅샷 기반 저장은 1건만 성공해야 함
  },
});

function readForm() {
  const res = http.get(`${BASE_URL}/hr/employees/${TARGET_ID}/info`, aux("GET /hr/employees/{id}/info"));
  const d = doc(res);
  const opts = (id) => d.find(`#${id} option`).toArray().map((o) => o.attr("value")).filter((v) => v);
  return {
    csrf: csrfFrom(res.body),
    form: {
      name: inputValue(d, "#name"),
      email: inputValue(d, "#email"),
      phone: inputValue(d, "#phone"),
      address: inputValue(d, "#address"),
      positionId: selectedValue(d, "positionId"),
      departmentId: selectedValue(d, "departmentId"),
      version: inputValue(d, 'input[name="version"]'),
    },
    positions: opts("positionId"),
    departments: opts("departmentId"),
  };
}

function clean(form) {
  const f = Object.assign({}, form);
  if (!f.version) delete f.version;
  return f;
}

export function setup() {
  const session = sharedLogin(USERS.hr, `/hr/employees/${TARGET_ID}/info`);
  const snap = readForm();
  const altPos = snap.positions.filter((p) => p !== snap.form.positionId);
  const altDept = snap.departments.filter((p) => p !== snap.form.departmentId);
  if (!altPos.length || !altDept.length) throw new Error("[setup] 바꿀 수 있는 다른 직책/부서가 없습니다.");
  console.log(`[setup] 사원 ${TARGET_ID} 스냅샷: position=${snap.form.positionId}, department=${snap.form.departmentId}, version=${snap.form.version || "(없음)"}`);
  return Object.assign(session, { snap, altPos, altDept });
}

export default function (data) {
  useSharedSession(data);
  const i = exec.vu.idInTest;
  const form = clean(data.snap.form);
  if (i % 2 === 1) form.positionId = data.altPos[i % data.altPos.length];
  else form.departmentId = data.altDept[i % data.altDept.length];

  const res = http.post(
    `${BASE_URL}/hr/employees/${TARGET_ID}/assignment`,
    withCsrf(form, data.snap.csrf || data.csrf),
    target("POST /hr/employees/{id}/assignment (stale snapshot)", { redirects: 0 })
  );
  const ok = res.status === 302 && location(res).includes(`/hr/employees/${TARGET_ID}/info`);
  // 낙관적 락 충돌을 같은 화면 + 에러 플래시로 처리했을 수도 있으니, 필요하면 아래 판정을 수정
  if (ok && !/error|conflict|충돌/i.test(location(res))) accepted.add(1);
  else rejected.add(1);
}

export function teardown(data) {
  useSharedSession(data);
  const now = readForm();
  console.log(`[결과] 최종 position=${now.form.positionId}, department=${now.form.departmentId}, version=${now.form.version || "(없음)"}`);

  // 원복 (현재 version 기준으로)
  const restore = clean(Object.assign({}, data.snap.form, { version: now.form.version }));
  const r = http.post(`${BASE_URL}/hr/employees/${TARGET_ID}/assignment`, withCsrf(restore, now.csrf), aux("POST restore", { redirects: 0 }));
  console.log(`[restore] status=${r.status} → 스냅샷 값으로 복원`);
}

export const handleSummary = makeSummary("REQ-NF-H-009", "직책/부서 변경 Lost Update", "race");
