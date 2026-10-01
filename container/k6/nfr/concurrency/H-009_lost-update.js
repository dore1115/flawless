// REQ-NF-H-009 (직책/부서 단일 변경 — Lost Update)
// 대상: POST /hr/employees/{id}/assignment
//
// 시나리오: 인사관리자 여러 명이 "같은 시점에 연 수정 화면(같은 스냅샷)"을 기준으로 동시에 저장한다.
//   - 홀수 VU: 직책만 바꿈 (부서는 스냅샷 값) / 짝수 VU: 부서만 바꿈 (직책은 스냅샷 값)
//
// 판정은 개선 방식에 따라 두 단계로 나뉜다.
//   (A) 트랜잭션 겹침 방지 — @Version + saveAndFlush, 충돌 시 409
//       seed: 409가 한 건도 없음  /  fix: 거의 동시에 들어온 요청 중 일부가 409로 거절됨
//       → nfr_update_conflict_409 > 0 이면 개선됨
//   (B) 오래된 화면 기준 저장 방지 — 폼에 version hidden 필드를 싣고 서버가 비교
//       → 스냅샷 기반 저장은 1건만 성공해야 함 (nfr_update_accepted == 1)
//   현재 fix-REQ-NF-H-009 구현은 (A)까지다 (info.html에 version 필드 없음).
//   그래서 (B) 기준으로 보면 fix에서도 여러 건이 성공한다 — 발표 때 "남은 한계"로 설명할 것.
//   폼에 version 필드가 생기면 스크립트가 자동으로 함께 보낸다.
// 끝나면 스냅샷 값으로 원복한다.
import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { Counter } from "k6/metrics";
import { BASE_URL, USERS, sharedLogin, useSharedSession, target, aux, doc, inputValue, selectedValue, csrfFrom, withCsrf, location } from "../lib/common.js";
import { commonOptions, makeSummary } from "../lib/profiles.js";

const TARGET_ID = __ENV.TARGET_ID || "50";
const RACE_VUS = Number(__ENV.RACE_VUS || 20);

const accepted = new Counter("nfr_update_accepted");
const conflicts = new Counter("nfr_update_conflict_409");
const rejected = new Counter("nfr_update_rejected_other");

export const options = Object.assign({}, commonOptions, {
  scenarios: { race: { executor: "per-vu-iterations", vus: RACE_VUS, iterations: 1, maxDuration: "1m" } },
  thresholds: {
    "http_req_duration{kind:target}": ["max>=0"],
    "http_reqs{kind:target}": ["count>=0"],
    "http_req_failed{kind:target}": ["rate>=0"],
    nfr_update_accepted: ["count>=0"],
    nfr_update_conflict_409: ["count>=0"],
    nfr_update_rejected_other: ["count>=0"],
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
  if (ok) accepted.add(1);
  else if (res.status === 409) conflicts.add(1);
  else rejected.add(1);
}

export function teardown(data) {
  useSharedSession(data);
  const now = readForm();
  console.log(`[결과] 최종 position=${now.form.positionId}, department=${now.form.departmentId}, version=${now.form.version || "(없음)"}`);
  console.log("[판정] (A) 409 건수 > 0 → 트랜잭션 겹침 방지됨 / (B) accepted == 1 → 오래된 화면 기준 저장까지 방지됨 (요약의 nfr_update_* 참고)");

  // 원복 (현재 version 기준으로)
  const restore = clean(Object.assign({}, data.snap.form, { version: now.form.version }));
  const r = http.post(`${BASE_URL}/hr/employees/${TARGET_ID}/assignment`, withCsrf(restore, now.csrf), aux("POST restore", { redirects: 0 }));
  console.log(`[restore] status=${r.status} → 스냅샷 값으로 복원`);
}

export const handleSummary = makeSummary("REQ-NF-H-009", "직책/부서 변경 Lost Update", "race");
