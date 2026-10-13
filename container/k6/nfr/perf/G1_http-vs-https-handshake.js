// G1 (HTTP 평문 통신) — HTTP vs HTTPS 초기 접속 비용 측정
//
// 측정 방식
//   - noVUConnectionReuse: 이터레이션이 끝나면 연결을 버린다 → 매 이터레이션의 첫 요청은 항상 새 연결(cold)
//   - 같은 이터레이션 안의 두 번째 요청은 연결을 재사용(warm)
//     cold - warm  = 연결 수립 비용 (TCP + TLS 핸드셰이크)
//     https warm - http warm = 연결 이후 암호화 자체의 오버헤드
//   - VU 1개로 순차 실행 (부하에 의한 지연이 섞이지 않게), 프로토콜 순서는 이터레이션마다 번갈아 순서 효과 상쇄
//   - 앞 WARMUP회는 JIT 워밍업 구간으로 보고 집계에서 제외
//
// 실행 예
//   # 둘 다 동시에 (HTTP 커넥터와 HTTPS 커넥터가 같이 떠 있을 때) — 권장
//   k6 run -e HTTP_URL=http://localhost:8081 -e HTTPS_URL=https://localhost:8080 G1_http-vs-https-handshake.js
//   # 한쪽만 (seed 컨테이너=HTTP, fix 컨테이너=HTTPS 를 따로 띄워 각각 측정할 때)
//   k6 run -e HTTP_URL=http://localhost:8080 -e LABEL=seed G1_http-vs-https-handshake.js
//   k6 run -e HTTPS_URL=https://localhost:8080 -e LABEL=fix  G1_http-vs-https-handshake.js
//
// 환경변수
//   HTTP_URL / HTTPS_URL  측정 대상 base URL (최소 하나)
//   TARGET       요청 경로. 인증 없이 가볍게 응답하는 경로 권장 (기본 /login)
//   ITERATIONS   집계할 반복 수 (기본 300)
//   WARMUP       집계 제외 반복 수 (기본 30)
//   LABEL        결과 파일명 라벨 (기본 compare)
import http from "k6/http";
import { check } from "k6";
import { Trend, Counter } from "k6/metrics";

const HTTP_URL = __ENV.HTTP_URL || "";
const HTTPS_URL = __ENV.HTTPS_URL || "";
const TARGET = __ENV.TARGET || "/login";
const ITERATIONS = Number(__ENV.ITERATIONS || 300);
const WARMUP = Number(__ENV.WARMUP || 30);
const LABEL = __ENV.LABEL || "compare";

const PROTOS = [];
if (HTTP_URL) PROTOS.push({ name: "http", base: HTTP_URL.replace(/\/$/, "") });
if (HTTPS_URL) PROTOS.push({ name: "https", base: HTTPS_URL.replace(/\/$/, "") });
if (PROTOS.length === 0) throw new Error("HTTP_URL 또는 HTTPS_URL 중 하나 이상을 지정하세요");

const PHASES = ["cold", "warm"];
const FIELDS = ["connect", "tls", "ttfb", "total"];
const T = {};
for (const p of ["http", "https"]) {
  for (const ph of PHASES) {
    for (const f of FIELDS) T[`${p}_${ph}_${f}`] = new Trend(`${p}_${ph}_${f}`, true);
  }
}
const warmNotReused = new Counter("warm_not_reused"); // warm인데 새 연결이 생긴 횟수 (0이어야 정상)
const coldReused = new Counter("cold_reused");       // cold인데 연결이 재사용된 횟수 (0이어야 정상)

export const options = {
  insecureSkipTLSVerify: true, // 자체서명 인증서 허용
  noVUConnectionReuse: true,
  scenarios: {
    handshake: {
      executor: "per-vu-iterations",
      vus: 1,
      iterations: ITERATIONS + WARMUP,
      maxDuration: "15m",
    },
  },
  summaryTrendStats: ["avg", "p(50)", "p(95)", "p(99)", "max"],
};

// 협상된 TLS 버전 / 암호군 / HTTP 버전을 결과에 남기기 위한 사전 요청
export function setup() {
  const info = {};
  for (const p of PROTOS) {
    const r = http.get(p.base + TARGET, { redirects: 0 });
    info[p.name] = {
      status: r.status,
      httpVersion: r.proto,
      tlsVersion: r.tls_version || null,
      cipher: r.tls_cipher_suite || null,
    };
  }
  return info;
}

export default function () {
  const order = __ITER % 2 === 0 ? PROTOS : [...PROTOS].reverse();
  for (const p of order) {
    const url = p.base + TARGET;
    const cold = http.get(url, { redirects: 0, tags: { proto: p.name, phase: "cold" } });
    const warm = http.get(url, { redirects: 0, tags: { proto: p.name, phase: "warm" } });

    check(cold, { "응답 2xx/3xx": (r) => r.status >= 200 && r.status < 400 });
    if (__ITER < WARMUP) continue;

    record(p.name, "cold", cold);
    record(p.name, "warm", warm);
  }
}

function record(proto, phase, r) {
  const t = r.timings;
  T[`${proto}_${phase}_connect`].add(t.connecting);
  T[`${proto}_${phase}_tls`].add(t.tls_handshaking);
  T[`${proto}_${phase}_ttfb`].add(t.waiting);
  T[`${proto}_${phase}_total`].add(t.blocked + t.connecting + t.tls_handshaking + t.sending + t.waiting + t.receiving);
  if (phase === "warm" && t.connecting > 0) warmNotReused.add(1);
  if (phase === "cold" && t.connecting === 0) coldReused.add(1);
}

export function handleSummary(data) {
  const v = (name, stat) => {
    const m = data.metrics[name];
    return m && m.values[stat] !== undefined ? m.values[stat] : null;
  };
  const r3 = (x) => (x === null ? null : Math.round(x * 1000) / 1000);

  const results = {};
  for (const p of PROTOS) {
    results[p.name] = {};
    for (const ph of PHASES) {
      results[p.name][ph] = {};
      for (const f of FIELDS) {
        const n = `${p.name}_${ph}_${f}`;
        results[p.name][ph][f] = { avg: r3(v(n, "avg")), p50: r3(v(n, "p(50)")), p95: r3(v(n, "p(95)")), p99: r3(v(n, "p(99)")) };
      }
    }
  }

  const diff = {};
  if (results.http && results.https) {
    const d = (a, b) => (a === null || b === null ? null : r3(a - b));
    diff.coldTotalP50 = d(results.https.cold.total.p50, results.http.cold.total.p50);
    diff.coldTotalP95 = d(results.https.cold.total.p95, results.http.cold.total.p95);
    diff.warmTotalP50 = d(results.https.warm.total.p50, results.http.warm.total.p50);
    diff.warmTotalP95 = d(results.https.warm.total.p95, results.http.warm.total.p95);
  }

  const out = {
    req: "G1",
    describe: "HTTP vs HTTPS 초기 접속 비용",
    label: LABEL,
    target: TARGET,
    iterations: ITERATIONS,
    warmupExcluded: WARMUP,
    finishedAt: new Date().toISOString(),
    negotiated: data.setup_data || {},
    unit: "ms",
    results,
    httpsMinusHttp: diff,
    sanity: {
      warmNotReused: v("warm_not_reused", "count") || 0,
      coldReused: v("cold_reused", "count") || 0,
    },
  };

  // 콘솔 표
  const pad = (s, n) => String(s).padEnd(n);
  const num = (x) => (x === null ? "-" : x.toFixed(3));
  let txt = `\nG1 HTTP vs HTTPS 초기 접속 (${TARGET}, ${ITERATIONS}회, 단위 ms, p50 / p95)\n`;
  txt += pad("proto", 7) + pad("phase", 7) + FIELDS.map((f) => pad(f, 20)).join("") + "\n";
  for (const p of PROTOS) {
    for (const ph of PHASES) {
      txt += pad(p.name, 7) + pad(ph, 7);
      txt += FIELDS.map((f) => pad(`${num(results[p.name][ph][f].p50)} / ${num(results[p.name][ph][f].p95)}`, 20)).join("");
      txt += "\n";
    }
  }
  if (diff.coldTotalP50 !== undefined) {
    txt += `\nHTTPS - HTTP  cold total p50 ${num(diff.coldTotalP50)} / p95 ${num(diff.coldTotalP95)}   (초기 접속 추가 비용)\n`;
    txt += `HTTPS - HTTP  warm total p50 ${num(diff.warmTotalP50)} / p95 ${num(diff.warmTotalP95)}   (연결 재사용 시 암호화 오버헤드)\n`;
  }
  for (const [k, info] of Object.entries(out.negotiated)) {
    txt += `${k}: ${info.httpVersion}${info.tlsVersion ? `, ${info.tlsVersion}, ${info.cipher}` : ""} (status ${info.status})\n`;
  }
  if (out.sanity.warmNotReused || out.sanity.coldReused) {
    txt += `⚠ 연결 재사용 상태가 예상과 다름: warmNotReused=${out.sanity.warmNotReused}, coldReused=${out.sanity.coldReused}\n`;
  }

  const ts = out.finishedAt.replace(/[:.]/g, "-");
  return {
    stdout: txt,
    [`results/G1_${LABEL}_handshake_${ts}.json`]: JSON.stringify(out, null, 2),
  };
}
