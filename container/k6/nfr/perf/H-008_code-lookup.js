// REQ-NF-H-008 (직책/부서 코드 기준 소속 사원 조회 — 인덱스 부재)
// 대상: POST /hr/positions/{id}/delete , POST /hr/departments/{id}/delete
//       → 삭제 전 employeeRepository.findByPosition()/findByDepartment()로 소속 사원을 조회하고,
//         소속 사원이 있으면 삭제가 "차단"되어 목록으로 302 리다이렉트된다.
//
// ⚠ 반드시 "소속 사원이 있는" 직책/부서 ID에만 요청해야 데이터가 안 바뀐다.
//   setup()에서 사원 목록을 읽어 소속 인원이 1명 이상인 ID만 골라내고, 없으면 테스트를 중단한다.
//
// ⚠ 재현성 주의: schema.sql의 employee.position_id / department_id 에는 FK가 걸려 있어서
//   InnoDB가 FK 컬럼 인덱스를 "자동으로" 만든다 → 지금 스키마로는 Full scan이 재현되지 않을 가능성이 큼.
//   측정 전에 README의 EXPLAIN 확인 절차를 먼저 볼 것.
import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, USERS, sharedLogin, useSharedSession, target, aux, doc, withCsrf, location, pick, followRedirect } from "../lib/common.js";
import { PROFILE, commonOptions, scenarioFor, thresholdsFor, tagStage, makeSummary } from "../lib/profiles.js";

export const options = Object.assign({}, commonOptions, {
  scenarios: { lookup: scenarioFor("default", { RATE: 20 }) },
  thresholds: thresholdsFor(["POST /hr/positions/{id}/delete (blocked)", "POST /hr/departments/{id}/delete (blocked)"]),
});

function rowsOf(listPath, kind) {
  const res = http.get(`${BASE_URL}${listPath}`, aux(`GET ${listPath}`));
  const rows = [];
  for (const tr of doc(res).find("tbody tr").toArray()) {
    const action = tr.find("form").attr("action") || "";
    const m = action.match(new RegExp(`/hr/${kind}/(\\d+)/delete`));
    if (m) rows.push({ id: m[1], name: tr.find("td").first().text().trim() });
  }
  return rows;
}

export function setup() {
  const session = sharedLogin(USERS.hr, "/hr/positions", Number(__ENV.SHARED_SESSIONS || 20));

  const emp = doc(http.get(`${BASE_URL}/hr/employees`, aux("GET /hr/employees")));
  const deptCount = {};
  const posCount = {};
  for (const tr of emp.find("tbody tr").toArray()) {
    const tds = tr.find("td");
    const dept = tds.eq(1).text().trim();
    const pos = tds.eq(2).text().trim();
    deptCount[dept] = (deptCount[dept] || 0) + 1;
    posCount[pos] = (posCount[pos] || 0) + 1;
  }

  const targets = [];
  for (const r of rowsOf("/hr/positions", "positions")) if (posCount[r.name] > 0) targets.push({ kind: "positions", ...r, holders: posCount[r.name] });
  for (const r of rowsOf("/hr/departments", "departments")) if (deptCount[r.name] > 0) targets.push({ kind: "departments", ...r, holders: deptCount[r.name] });

  if (!targets.length) throw new Error("[setup] 소속 사원이 있는 직책/부서를 찾지 못했습니다. (삭제가 실제로 일어날 수 있어 중단)");
  console.log(`[setup] 대상 ${targets.length}건: ${targets.map((t) => `${t.kind}/${t.id}(${t.name}:${t.holders}명)`).join(", ")}`);
  return Object.assign(session, { targets });
}

export default function (data) {
  tagStage();
  const sess = useSharedSession(data);
  const t = pick(data.targets);
  const res = http.post(
    `${BASE_URL}/hr/${t.kind}/${t.id}/delete`,
    withCsrf({}, sess.csrf),
    target(`POST /hr/${t.kind}/{id}/delete (blocked)`, { redirects: 0 })
  );
  check(res, { "302 → 목록": (r) => r.status === 302 && location(r).includes(`/hr/${t.kind}`) });
  // 차단 시 소속 사원 목록이 flash attribute로 세션에 저장됨 → 목록 GET으로 꺼내 비움 (측정 제외)
  followRedirect(res, `GET /hr/${t.kind} (flash 소비)`);
  if (PROFILE === "smoke" || PROFILE === "vus") sleep(Number(__ENV.THINK_S || 1));
}

export const handleSummary = makeSummary("REQ-NF-H-008", "직책/부서 코드 기준 소속 사원 조회");
