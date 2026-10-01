// REQ-NF-I-003 (재고 조회 N+1) / REQ-NF-I-004 (페이지네이션 부재)
// 대상: GET /inventory/items?page=&name=   (IV-002 재고 조회/검색/페이징)
//
// 결함: InventoryService.getFilteredActiveItems()가 전체 품목을 한 번에 읽고 subList로 자름(I-004)
//       + getCurrentQuantities()가 품목마다 stockRepository.findByItemId() 개별 호출(I-003)
//
// 시드 품목은 50건뿐이라 I-004가 거의 안 보인다 → data/z-scale-up.sql 로 5,000건 이상 만든 뒤 측정.
// MAX_PAGE는 (품목 수 / 10) - 1 로 맞출 것 (시드 50건 = 4, 5,050건 = 504)
import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import { BASE_URL, employeeUserFor, ensureSession, resetSession, target, landedOn, pick } from "../lib/common.js";
import { PROFILE, commonOptions, scenarioFor, thresholdsFor, tagStage, makeSummary } from "../lib/profiles.js";

const MAX_PAGE = Number(__ENV.MAX_PAGE || 4);
const SEARCH_RATIO = Number(__ENV.SEARCH_RATIO || 0.3);
const KEYWORDS = (__ENV.KEYWORDS || "모니터,케이블,용지,부하테스트").split(",");

export const options = Object.assign({}, commonOptions, {
  scenarios: { stock: scenarioFor("default", { RATE: 20 }) },
  thresholds: thresholdsFor(["GET /inventory/items (page)", "GET /inventory/items (search)"]),
});

export default function () {
  tagStage();
  if (!ensureSession(employeeUserFor(exec.vu.idInTest))) return;

  let res;
  if (Math.random() < SEARCH_RATIO) {
    const kw = encodeURIComponent(pick(KEYWORDS));
    res = http.get(`${BASE_URL}/inventory/items?name=${kw}&page=0`, target("GET /inventory/items (search)"));
  } else {
    const page = Math.floor(Math.random() * (MAX_PAGE + 1));
    res = http.get(`${BASE_URL}/inventory/items?page=${page}`, target("GET /inventory/items (page)"));
  }

  const ok = check(res, {
    "status 200": (r) => r.status === 200,
    "재고 화면 도착(세션 유지)": (r) => landedOn(r, "/inventory/items"),
  });
  if (!ok && !landedOn(res, "/inventory/items")) resetSession();

  if (PROFILE === "smoke" || PROFILE === "vus") sleep(Number(__ENV.THINK_S || 1));
}

export const handleSummary = makeSummary("REQ-NF-I-003-004", "재고 조회/검색/페이징");
