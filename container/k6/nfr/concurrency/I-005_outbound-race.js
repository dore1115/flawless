// REQ-NF-I-005 (재고 출고 동시성 — 락 부재로 인한 Lost Update / 초과 출고)
// 대상: POST /inventory/outbound {itemId, quantity=1}
//
// 방법: 재고 Q0개인 품목 하나에 RACE_VUS명이 동시에 ITER번씩 "1개 출고"를 요청한다.
//   정상(락 적용): 성공 건수 == 실제 감소량,  성공 건수 ≤ Q0
//   결함(락 없음): 성공 건수 >  실제 감소량  (= Lost Update, 출고 이력은 남았는데 재고는 덜 빠짐)
//
// 성공 건수는 k6 카운터 대신 "품목 입출고 이력(/inventory/{id}/history)의 출고 행 증가분"으로 센다
// (teardown에서는 k6 카운터 값을 읽을 수 없기 때문).
// 끝나면 RESTORE=true(기본)일 때 입고 처리로 재고를 Q0로 되돌린다 (입고 이력 1건 추가됨).
import http from "k6/http";
import { check } from "k6";
import { Counter, Gauge } from "k6/metrics";
import { BASE_URL, USERS, sharedLogin, useSharedSession, target, aux, doc, csrfFrom, withCsrf, location } from "../lib/common.js";
import { commonOptions, makeSummary } from "../lib/profiles.js";

const ITEM_ID = __ENV.ITEM_ID || "23"; // 시드 기준 HDMI 케이블, 재고 30
const RACE_VUS = Number(__ENV.RACE_VUS || 50);
const ITER = Number(__ENV.ITER || 2);

const accepted = new Counter("nfr_outbound_accepted");
const rejected = new Counter("nfr_outbound_rejected_insufficient");
const errors = new Counter("nfr_outbound_5xx");
const gQ0 = new Gauge("nfr_stock_before");
const gQ1 = new Gauge("nfr_stock_after");
const gSucc = new Gauge("nfr_outbound_success_by_history");
const gLost = new Gauge("nfr_lost_update_count");
const gOver = new Gauge("nfr_oversold_count");

export const options = Object.assign({}, commonOptions, {
  scenarios: {
    race: { executor: "per-vu-iterations", vus: RACE_VUS, iterations: ITER, maxDuration: "2m" },
  },
  thresholds: {
    "http_req_duration{kind:target}": ["max>=0"],
    "http_req_failed{kind:target}": ["rate>=0"],
    "http_reqs{kind:target}": ["count>=0"],
    checks: ["rate==1.0"],
  },
});

function readStock() {
  const res = http.get(`${BASE_URL}/inventory/outbound`, aux("GET /inventory/outbound"));
  const q = doc(res).find(`#itemId option[value="${ITEM_ID}"]`).attr("data-quantity");
  if (q === undefined) throw new Error(`품목 ${ITEM_ID}를 출고 화면에서 찾지 못했습니다 (삭제됐거나 ID 오류)`);
  return { qty: Number(q), csrf: csrfFrom(res.body) };
}

function countOutRows() {
  const res = http.get(`${BASE_URL}/inventory/${ITEM_ID}/history`, aux("GET /inventory/{id}/history"));
  // 출고 행 1건마다 "출고" 라벨과 "-N" 수량 두 곳에 text-red-600 font-medium이 붙으므로 라벨만 센다
  return (res.body.match(/text-red-600 font-medium">\s*출고\s*</g) || []).length;
}

export function setup() {
  const session = sharedLogin(USERS.iv, "/inventory/outbound");
  const { qty } = readStock();
  const total = RACE_VUS * ITER;
  console.log(`[setup] item=${ITEM_ID} 재고 Q0=${qty}, 총 출고 요청=${total}건 (1개씩)`);
  if (qty > total) console.warn(`[setup] Q0(${qty}) > 요청 수(${total}) → 재고 부족 분기가 안 생김. RACE_VUS/ITER를 늘리면 초과 출고까지 볼 수 있음`);
  return Object.assign(session, { q0: qty, out0: countOutRows() });
}

export default function (data) {
  useSharedSession(data);
  const res = http.post(
    `${BASE_URL}/inventory/outbound`,
    withCsrf({ itemId: ITEM_ID, quantity: "1" }, data.csrf),
    target("POST /inventory/outbound", { redirects: 0 })
  );
  if (res.status === 302 && location(res).includes("/inventory/outbound")) accepted.add(1);
  else if (res.status === 200) rejected.add(1); // 폼 재표시 = 재고 부족(또는 검증 실패)
  else if (res.status >= 500) errors.add(1);
}

export function teardown(data) {
  useSharedSession(data);
  const { qty: q1, csrf } = readStock();
  const success = countOutRows() - data.out0;
  const decreased = data.q0 - q1;
  const lost = success - decreased;
  const oversold = Math.max(0, success - data.q0);

  gQ0.add(data.q0);
  gQ1.add(q1);
  gSucc.add(success);
  gLost.add(lost);
  gOver.add(oversold);

  console.log(`[결과] Q0=${data.q0} → Q1=${q1} (감소 ${decreased}), 출고 이력 증가 ${success}건, lost update=${lost}, 초과 출고=${oversold}`);
  check(null, {
    "Lost Update 없음 (출고 성공 건수 == 실제 재고 감소량)": () => lost === 0,
    "초과 출고 없음 (성공 건수 ≤ Q0)": () => oversold === 0,
  });

  if ((__ENV.RESTORE || "true") === "true" && decreased > 0) {
    http.post(`${BASE_URL}/inventory/inbound`, withCsrf({ itemId: ITEM_ID, quantity: String(decreased) }, csrf), aux("POST /inventory/inbound (restore)", { redirects: 0 }));
    console.log(`[restore] 입고 ${decreased}개로 재고 복원 → ${readStock().qty}`);
  }
}

export const handleSummary = makeSummary("REQ-NF-I-005", "재고 출고 동시성", "race");
