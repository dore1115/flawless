// ─────────────────────────────────────────────────────────────
// 부하 프로파일 / 판정 기준 / 결과 요약 (REQ-NF-G-007 측정 표준)
//
// PROFILE
//   smoke      : 1 VU, 30s. 스크립트 검증 + 무부하 기준선(baseline) p99 확보
//   load       : 고정 도착률(RATE req/s) DURATION 동안. "개선 전/후 동일 부하 비교"용
//   breakpoint : 도착률을 계단식으로 올리며(START→MAX, STEP씩, 각 STEP_HOLD_S초 유지)
//                "서비스 불능 지점"을 찾는다. 계단별 p50/p95/p99, 달성 RPS, 에러율을 요약에 출력
//   vus        : 고정 동시 사용자(VUS) DURATION 동안 (닫힌 모델, 예: REQ-NF-L-005 "동시 사용자 100명")
//
// 서비스 불능 판정 (계단 1개 = 유지구간 60초 단위로 판정, 하나라도 해당되면 불능)
//   1) p99 ≥ P99_LIMIT_MS (기본 3000ms)
//   2) 에러율 ≥ ERR_LIMIT (기본 5%)
//   3) 달성 처리량 < 목표 도착률의 90%  (k6가 요청을 못 보내고 dropped 되는 상태 = 포화)
// ─────────────────────────────────────────────────────────────
import exec from "k6/execution";

export const PROFILE = (__ENV.PROFILE || "smoke").toLowerCase();
export const LABEL = __ENV.LABEL || "seed"; // seed(결함) / fix(개선) / alt ...
export const P99_LIMIT_MS = Number(__ENV.P99_LIMIT_MS || 3000);
export const SLO_P95_MS = Number(__ENV.SLO_P95_MS || 1000);
export const ERR_LIMIT = Number(__ENV.ERR_LIMIT || 0.05);
const THROUGHPUT_RATIO = Number(__ENV.THROUGHPUT_RATIO || 0.9);

const START_RPS = Number(__ENV.START_RPS || 5);
const STEP_RPS = Number(__ENV.STEP_RPS || 5);
const MAX_RPS = Number(__ENV.MAX_RPS || 100);
const STEP_RAMP_S = Number(__ENV.STEP_RAMP_S || 10);
const STEP_HOLD_S = Number(__ENV.STEP_HOLD_S || 60);

const STEPS = [];
for (let r = START_RPS; r <= MAX_RPS; r += STEP_RPS) STEPS.push(r);

// 계단 유지구간만 h01, h02 ... 로 태깅 (램프 구간은 'ramp' → 집계 제외)
const TIMELINE = (() => {
  let t = 0;
  return STEPS.map((rps, i) => {
    t += STEP_RAMP_S;
    const s = { from: t, to: t + STEP_HOLD_S, label: "h" + String(i + 1).padStart(2, "0"), rps };
    t += STEP_HOLD_S;
    return s;
  });
})();

export const commonOptions = {
  insecureSkipTLSVerify: true, // REQ-NF-G-001 수정 후 자체 서명 인증서 대응
  noCookiesReset: true, // k6는 기본적으로 iteration마다 쿠키를 지움 → VU별 로그인 세션 유지용
  maxRedirects: 5,
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max", "count"],
  discardResponseBodies: false,
  tags: { req: __ENV.REQ || "", label: LABEL },
};

/** 현재 프로파일에 맞는 scenario 객체. defaults로 스크립트별 기본값을 줄 수 있다 */
export function scenarioFor(execName = "default", defaults = {}) {
  const env = (k, d) => (__ENV[k] !== undefined ? __ENV[k] : defaults[k] !== undefined ? defaults[k] : d);
  const rate = Number(env("RATE", 10));
  const maxVUs = Number(env("MAX_VUS", 1000));
  switch (PROFILE) {
    case "smoke":
      return { executor: "constant-vus", exec: execName, vus: 1, duration: env("DURATION", "30s") };
    case "load":
      return {
        executor: "constant-arrival-rate",
        exec: execName,
        rate,
        timeUnit: "1s",
        duration: env("DURATION", "3m"),
        preAllocatedVUs: Math.min(maxVUs, Math.max(10, rate * 2)),
        maxVUs,
      };
    case "breakpoint":
      return {
        executor: "ramping-arrival-rate",
        exec: execName,
        startRate: STEPS[0],
        timeUnit: "1s",
        preAllocatedVUs: Math.min(maxVUs, Number(env("PRE_VUS", 50))),
        maxVUs,
        stages: STEPS.flatMap((r) => [
          { duration: `${STEP_RAMP_S}s`, target: r },
          { duration: `${STEP_HOLD_S}s`, target: r },
        ]),
      };
    case "vus":
      return { executor: "constant-vus", exec: execName, vus: Number(env("VUS", 50)), duration: env("DURATION", "2m") };
    default:
      throw new Error(`알 수 없는 PROFILE: ${PROFILE} (smoke|load|breakpoint|vus)`);
  }
}

/**
 * 기본 threshold.
 *  names: 요청 이름(tag name)별로도 p50/p95/p99를 따로 보고 싶을 때 지정
 */
export function thresholdsFor(names = [], extra = {}) {
  const t = {
    "http_req_duration{kind:target}": [`p(95)<${SLO_P95_MS}`, `p(99)<${P99_LIMIT_MS}`],
    "http_req_failed{kind:target}": [`rate<${ERR_LIMIT}`],
    "http_reqs{kind:target}": ["count>=0"],
  };
  for (const n of names) {
    t[`http_req_duration{kind:target,name:${n}}`] = ["max>=0"];
    t[`http_req_failed{kind:target,name:${n}}`] = ["rate>=0"];
  }
  if (PROFILE === "breakpoint") {
    const abort = (__ENV.ABORT_ON_BREAK || "false") === "true";
    for (const s of TIMELINE) {
      t[`http_req_duration{kind:target,stage:${s.label}}`] = [
        abort ? { threshold: `p(99)<${P99_LIMIT_MS}`, abortOnFail: true, delayAbortEval: "20s" } : `p(99)<${P99_LIMIT_MS}`,
      ];
      t[`http_req_failed{kind:target,stage:${s.label}}`] = [`rate<${ERR_LIMIT}`];
      t[`http_reqs{kind:target,stage:${s.label}}`] = ["count>=0"];
    }
    // 안전장치: 앱이 완전히 죽은 채로 계단을 계속 올리지 않도록
    t["http_req_failed"] = [{ threshold: "rate<0.5", abortOnFail: true, delayAbortEval: "1m" }];
  }
  return Object.assign(t, extra);
}

/** breakpoint 프로파일에서 iteration 시작 시 호출 → 현재 계단 태그 부여 */
export function tagStage() {
  if (PROFILE !== "breakpoint") return;
  const t = (Date.now() - exec.scenario.startTime) / 1000;
  const s = TIMELINE.find((x) => t >= x.from && t < x.to);
  exec.vu.metrics.tags.stage = s ? s.label : "ramp";
}

// ── 결과 요약 ────────────────────────────────────────────────
const r1 = (v) => (v === undefined || v === null || Number.isNaN(v) ? null : Math.round(v * 10) / 10);
const pct = (v) => (v === undefined || v === null ? null : Math.round(v * 10000) / 100);

function trend(m, key) {
  const x = m[key];
  if (!x) return null;
  const v = x.values;
  return { avg: r1(v.avg), p50: r1(v.med), p90: r1(v["p(90)"]), p95: r1(v["p(95)"]), p99: r1(v["p(99)"]), max: r1(v.max) };
}

export function makeSummary(reqId, describe = "", profileName = null) {
  return function (data) {
    const m = data.metrics;
    const durS = data.state.testRunDurationMs / 1000;
    const count = m["http_reqs{kind:target}"] ? m["http_reqs{kind:target}"].values.count : 0;
    const report = {
      req: reqId,
      describe,
      label: LABEL,
      profile: profileName || PROFILE,
      baseUrl: __ENV.BASE_URL || "http://localhost:8080",
      finishedAt: new Date().toISOString(),
      criteria: { p99LimitMs: P99_LIMIT_MS, sloP95Ms: SLO_P95_MS, errLimit: ERR_LIMIT, throughputRatio: THROUGHPUT_RATIO },
      overall: Object.assign({}, trend(m, "http_req_duration{kind:target}"), {
        requests: count,
        rps: r1(count / durS),
        errorRatePct: m["http_req_failed{kind:target}"] ? pct(m["http_req_failed{kind:target}"].values.rate) : null,
        droppedIterations: m.dropped_iterations ? m.dropped_iterations.values.count : 0,
      }),
      byName: {},
      steps: [],
      breakpoint: null,
      custom: {},
      checks: {},
    };

    for (const key of Object.keys(m)) {
      const nm = key.match(/^http_req_duration\{kind:target,name:(.+)\}$/);
      if (nm && m[key].values.count > 0) {
        const f = m[`http_req_failed{kind:target,name:${nm[1]}}`];
        report.byName[nm[1]] = Object.assign(trend(m, key), { errorRatePct: f ? pct(f.values.rate) : null });
      }
      if (key.startsWith("nfr_")) {
        const v = m[key].values;
        report.custom[key] = v.count !== undefined ? v.count : v.value !== undefined ? v.value : v;
      }
    }

    if (PROFILE === "breakpoint") {
      let lastOk = null;
      for (const s of TIMELINE) {
        const d = trend(m, `http_req_duration{kind:target,stage:${s.label}}`);
        const reqs = m[`http_reqs{kind:target,stage:${s.label}}`];
        if (!d || !reqs || reqs.values.count === 0) continue;
        const f = m[`http_req_failed{kind:target,stage:${s.label}}`];
        const err = f ? f.values.rate : 0;
        const achieved = reqs.values.count / STEP_HOLD_S;
        const reasons = [];
        if (d.p99 >= P99_LIMIT_MS) reasons.push(`p99 ${d.p99}ms ≥ ${P99_LIMIT_MS}ms`);
        if (err >= ERR_LIMIT) reasons.push(`에러율 ${pct(err)}% ≥ ${pct(ERR_LIMIT)}%`);
        if (achieved < s.rps * THROUGHPUT_RATIO) reasons.push(`처리량 ${r1(achieved)}/${s.rps} req/s (포화)`);
        const row = Object.assign({ stage: s.label, targetRps: s.rps, achievedRps: r1(achieved), errorRatePct: pct(err) }, d, {
          broken: reasons.length > 0,
          reasons,
        });
        report.steps.push(row);
        if (!row.broken && !report.breakpoint) lastOk = row;
        if (row.broken && !report.breakpoint) {
          report.breakpoint = { stage: s.label, targetRps: s.rps, reasons, lastStableRps: lastOk ? lastOk.targetRps : null, lastStableP99: lastOk ? lastOk.p99 : null };
        }
      }
    }

    // check 결과 (보안/동시성 스크립트의 판정용)
    const walk = (g) => {
      for (const c of g.checks || []) report.checks[(g.path ? g.path.replace(/^::/, "").replace(/::/g, " › ") + " › " : "") + c.name] = { passes: c.passes, fails: c.fails };
      for (const sub of g.groups || []) walk(sub);
    };
    walk(data.root_group);

    const slug = reqId.replace(/[^A-Za-z0-9\-]+/g, "_");
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    const dir = __ENV.RESULTS_DIR || "results";
    return {
      stdout: renderText(report),
      [`${dir}/${slug}_${LABEL}_${PROFILE}_${stamp}.json`]: JSON.stringify(report, null, 2),
    };
  };
}

function pad(s, n) {
  s = String(s === null || s === undefined ? "-" : s);
  return s.length >= n ? s : s + " ".repeat(n - s.length);
}

function renderText(r) {
  const L = [];
  L.push("");
  L.push(`══ ${r.req} ${r.describe ? "· " + r.describe : ""}`);
  L.push(`   label=${r.label} profile=${r.profile} base=${r.baseUrl}`);
  const o = r.overall;
  if (o.requests) {
    L.push(
      `   [대상 요청 전체] n=${o.requests} rps=${o.rps} err=${o.errorRatePct}% ` +
        `p50=${o.p50}ms p95=${o.p95}ms p99=${o.p99}ms max=${o.max}ms dropped=${o.droppedIterations}`
    );
  }
  for (const [n, v] of Object.entries(r.byName)) {
    L.push(`   - ${pad(n, 38)} p50=${v.p50}ms p95=${v.p95}ms p99=${v.p99}ms err=${v.errorRatePct}%`);
  }
  if (r.steps.length) {
    L.push("");
    L.push("   stage  target  achieved  err%    p50      p95      p99      판정");
    for (const s of r.steps) {
      L.push(
        `   ${pad(s.stage, 6)} ${pad(s.targetRps, 7)} ${pad(s.achievedRps, 9)} ${pad(s.errorRatePct, 7)} ` +
          `${pad(s.p50, 8)} ${pad(s.p95, 8)} ${pad(s.p99, 8)} ${s.broken ? "✗ " + s.reasons.join(", ") : "✓"}`
      );
    }
    L.push("");
    if (r.breakpoint) {
      L.push(`   ▶ 서비스 불능 지점: ${r.breakpoint.targetRps} req/s (${r.breakpoint.reasons.join(", ")})`);
      L.push(`   ▶ 마지막 안정 구간: ${r.breakpoint.lastStableRps ?? "없음"} req/s (p99 ${r.breakpoint.lastStableP99 ?? "-"}ms)`);
    } else {
      L.push(`   ▶ MAX_RPS까지 불능 지점 없음 → MAX_RPS를 올려서 다시 실행`);
    }
  }
  const custom = Object.entries(r.custom);
  if (custom.length) {
    L.push("");
    for (const [k, v] of custom) L.push(`   ${pad(k, 34)} ${typeof v === "object" ? JSON.stringify(v) : v}`);
  }
  const checks = Object.entries(r.checks);
  if (checks.length) {
    L.push("");
    for (const [k, v] of checks) L.push(`   ${v.fails === 0 ? "✓" : "✗"} ${k}  (${v.passes}/${v.passes + v.fails})`);
  }
  L.push("");
  return L.join("\n");
}
