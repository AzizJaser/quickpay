// Phase 8 — top-up comparison run.
//
// This exists to answer ONE question the plan reserved for Phase 8:
//   "which flow holds a hot row under load — P2P or top-up — and why?"
//
// Identical executor, stages and rates to p2p.js so the two numbers are comparable. The
// ONLY difference is the endpoint. Whatever gap appears between them is attributable to
// what each flow locks, not to how it was measured.
//
// Run AFTER p2p.js, so the P2P prediction cannot be influenced by seeing this result.
//   k6 run scaffolding/load-test/topup.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE = __ENV.BASE || 'http://localhost:8080';
const MAXRATE = parseInt(__ENV.MAXRATE || '1600', 10);
const RUN = __ENV.RUN || `topup-${Date.now()}`;

const wallets = new SharedArray('wallets', () =>
  JSON.parse(open('./wallets.json'))
);

const conflicts = new Counter('errors_conflict_409');
const serverErrors = new Counter('errors_5xx');
const otherErrors = new Counter('errors_other');
const okLatency = new Trend('latency_successful_topups', true);

export const options = {
  scenarios: {
    topup: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: [
        { target: 50,      duration: '20s' },
        { target: 200,     duration: '30s' },
        { target: 400,     duration: '30s' },
        { target: 600,     duration: '30s' },
        { target: 900,     duration: '30s' },
        { target: MAXRATE, duration: '40s' },
        { target: MAXRATE, duration: '30s' },
      ],
    },
  },
};

export default function () {
  // The credited wallet is spread across the whole pool, exactly as in p2p.js.
  // The debited side is whatever topUp() chooses — that is the variable under test.
  const w = wallets[Math.floor(Math.random() * wallets.length)];
  const key = `${RUN}-${__VU}-${__ITER}`;

  const res = http.post(
    `${BASE}/v1/transfer/top-up`,
    JSON.stringify({ walletNumber: w, amount: 1 }),
    {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
      tags: { name: 'topup' },
    }
  );

  check(res, { 'top-up accepted': (r) => r.status === 200 || r.status === 201 });

  if (res.status === 200 || res.status === 201) okLatency.add(res.timings.duration);
  else if (res.status === 409) conflicts.add(1);
  else if (res.status >= 500 || res.status === 0) serverErrors.add(1);
  else otherErrors.add(1);
}

export function handleSummary(data) {
  const m = data.metrics;
  const get = (n, f = 'count') => (m[n] && m[n].values ? m[n].values[f] : 0) || 0;
  return {
    stdout: [
      '',
      '==================== TOP-UP LOAD TEST ====================',
      `  offered peak rate   : ${MAXRATE} req/s`,
      `  completed requests  : ${get('http_reqs')}`,
      `  achieved throughput : ${(get('http_reqs', 'rate') || 0).toFixed(1)} req/s`,
      `  dropped iterations  : ${get('dropped_iterations')}`,
      '',
      `    p50 : ${(get('latency_successful_topups', 'med') || 0).toFixed(1)} ms`,
      `    p95 : ${(get('latency_successful_topups', 'p(95)') || 0).toFixed(1)} ms`,
      `    p99 : ${(get('latency_successful_topups', 'p(99)') || 0).toFixed(1)} ms`,
      '',
      `  5xx / connection    : ${get('errors_5xx')}`,
      `  409 conflict        : ${get('errors_conflict_409')}`,
      `  other               : ${get('errors_other')}`,
      '==========================================================',
      '',
    ].join('\n'),
  };
}
