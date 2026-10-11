// ─────────────────────────────────────────────────────────────
// 서버 측 지표 스냅샷 (Prometheus) — "같은 부하에서 서버가 얼마나 일했나"를 결과 JSON에 남긴다.
//
// 응답시간이 seed/fix 모두 몇 ms 수준이라 차이가 안 보이는 결함(H-007 N+1 등)은
// breakpoint 대신 고정 부하(PROFILE=load)에서 아래 값을 비교한다.
//   nfr_server_cpu_ms_per_req   요청 1건당 앱 프로세스 CPU 시간(ms)  ← 주 비교 지표
//   nfr_server_cpu_avg_cores    측정 구간 평균 CPU 사용(코어)
//   nfr_server_requests         서버가 처리한 요청 수(actuator 제외)
//   nfr_gc_major_count          major GC 횟수 (Serial GC면 전체 정지)
//   nfr_gc_pause_ms             GC 정지 시간 합계(ms)
//
// 사용: setup()에서 const prom = serverSnapshot(); 을 data에 담고,
//       teardown(data)에서 recordServerDelta(data.prom);
// PROM_URL (기본 http://localhost:9090, 도커 네트워크 안이면 http://prometheus:9090)
// Prometheus에 못 붙으면 경고만 남기고 측정은 그대로 진행한다.
// ─────────────────────────────────────────────────────────────
import http from "k6/http";
import { sleep } from "k6";
import { Gauge } from "k6/metrics";

const PROM_URL = (__ENV.PROM_URL || "http://localhost:9090").replace(/\/+$/, "");
const SCRAPE_WAIT_S = Number(__ENV.SCRAPE_WAIT_S || 11); // scrape_interval 5s × 2 + 여유

const Q = {
  cpuNs: 'sum(process_cpu_time_ns_total{service="erp-backend"})',
  reqs: 'sum(http_server_requests_seconds_count{service="erp-backend",uri!~"/actuator.*"})',
  gcMajor: 'sum(jvm_gc_pause_seconds_count{service="erp-backend",action=~".*major.*"})',
  gcPauseS: 'sum(jvm_gc_pause_seconds_sum{service="erp-backend"})',
};

const g = {
  cpuMsPerReq: new Gauge("nfr_server_cpu_ms_per_req"),
  cpuAvgCores: new Gauge("nfr_server_cpu_avg_cores"),
  reqs: new Gauge("nfr_server_requests"),
  gcMajor: new Gauge("nfr_gc_major_count"),
  gcPauseMs: new Gauge("nfr_gc_pause_ms"),
};

function query(q) {
  try {
    const r = http.get(`${PROM_URL}/api/v1/query?query=${encodeURIComponent(q)}`, {
      tags: { name: "prometheus", kind: "aux" },
      timeout: "5s",
      responseCallback: http.expectedStatuses({ min: 100, max: 599 }),
    });
    if (r.status !== 200) return null;
    const res = r.json("data.result");
    if (!res || !res.length) return null;
    return Number(res[0].value[1]);
  } catch (e) {
    return null;
  }
}

/** 부하 시작 직전 카운터 값 */
export function serverSnapshot() {
  sleep(SCRAPE_WAIT_S); // 로그인 등 setup 요청이 스냅샷에 반영되도록
  const s = { t: Date.now() };
  for (const k of Object.keys(Q)) s[k] = query(Q[k]);
  if (s.reqs === null) {
    console.warn(`[server-metrics] Prometheus(${PROM_URL})에서 앱 지표를 못 읽음 → 서버 지표 생략. ` +
      `PROM_URL 확인, Grafana에서 up{service="erp-backend"}=1 인지 확인`);
    return null;
  }
  if (s.cpuNs === null) console.warn("[server-metrics] process_cpu_time_ns_total 없음 → process_cpu_usage 평균으로 대체");
  return s;
}

/** 부하 종료 후 차이를 계산해 nfr_* 게이지로 기록 (teardown에서 호출) */
export function recordServerDelta(start) {
  if (!start) return;
  sleep(SCRAPE_WAIT_S); // 마지막 요청까지 scrape 되도록
  const end = { t: Date.now() };
  for (const k of Object.keys(Q)) end[k] = query(Q[k]);
  const dur = (end.t - start.t) / 1000;
  const d = (k) => (start[k] === null || end[k] === null ? null : end[k] - start[k]);

  let cpuS = d("cpuNs") !== null ? d("cpuNs") / 1e9 : null;
  if (cpuS === null) {
    const avg = query(`avg_over_time(process_cpu_usage{service="erp-backend"}[${Math.max(10, Math.round(dur))}s])`);
    const cnt = query('max(system_cpu_count{service="erp-backend"})') || 1;
    if (avg !== null) cpuS = avg * cnt * dur;
  }
  const reqs = d("reqs");
  if (reqs !== null) g.reqs.add(Math.round(reqs));
  if (cpuS !== null) {
    g.cpuAvgCores.add(Math.round((cpuS / dur) * 1000) / 1000);
    if (reqs) g.cpuMsPerReq.add(Math.round(((cpuS * 1000) / reqs) * 100) / 100);
  }
  if (d("gcMajor") !== null) g.gcMajor.add(Math.round(d("gcMajor")));
  if (d("gcPauseS") !== null) g.gcPauseMs.add(Math.round(d("gcPauseS") * 1000));
  console.log(`[server-metrics] ${Math.round(dur)}s, 요청 ${reqs}, CPU ${cpuS === null ? "-" : cpuS.toFixed(2) + "s"}, ` +
    `요청당 ${cpuS && reqs ? ((cpuS * 1000) / reqs).toFixed(2) : "-"}ms, major GC ${d("gcMajor")}`);
}
