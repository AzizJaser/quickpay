// Phase 8 — P2P breaking-TPS test.
//
// Target from the plan: 500 TPS on peer-to-peer transfer.
// Definition of done: name the breaking TPS AND a fix that moved it.
//
// EXECUTOR CHOICE MATTERS. This uses ramping-arrival-rate, not ramping-vus.
//   ramping-vus       holds N virtual users; if the service slows, they simply wait, so
//                     offered load falls with throughput and the system NEVER looks
//                     saturated. It measures latency at a concurrency, not a limit.
//   ramping-arrival-rate  targets a REQUEST RATE regardless of response time. When the
//                     service cannot keep up, k6 cannot start iterations on schedule and
//                     reports dropped_iterations. That is the breaking point, visible.
//
// Run:  k6 run scaffolding/load-test/p2p.js
//       k6 run -e BASE=http://localhost:8080 -e MAXRATE=2000 scaffolding/load-test/p2p.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE = __ENV.BASE || 'http://localhost:8080';
const MAXRATE = parseInt(__ENV.MAXRATE || '1600', 10);
const RUN = __ENV.RUN || `p2p-${Date.now()}`;

// SharedArray loads once per test rather than once per VU — 200 strings copied into
// every VU would waste memory the load generator needs for open connections.
const wallets = new SharedArray('wallets', () =>
  JSON.parse(open('./wallets.json'))
);

// Split the failures apart. A 400 "insufficient balance" means the seed was too small and
// the run is invalid; a 409 means idempotency keys collided and the run is invalid; a 500
// or a timeout is the service actually breaking. Lumping them into one error rate would
// hide which of those happened.
const insufficient = new Counter('errors_insufficient_balance');
const conflicts = new Counter('errors_conflict_409');
const serverErrors = new Counter('errors_5xx');
const otherErrors = new Counter('errors_other');
const okLatency = new Trend('latency_successful_transfers', true);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    p2p: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      // Pre-allocate generously: if k6 has to grow the VU pool mid-ramp it stalls, and
      // the stall is indistinguishable from the service slowing down.
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: [
        { target: 50,               duration: '20s' },  // warm-up: JIT, pool fill, plan cache
        { target: 200,              duration: '30s' },
        { target: 400,              duration: '30s' },
        { target: 600,              duration: '30s' },
        { target: 900,              duration: '30s' },
        { target: MAXRATE,          duration: '40s' },
        { target: MAXRATE,          duration: '30s' },  // hold at the top
      ],
    },
  },
  thresholds: {
    // Deliberately NOT abortOnFail. The point of this run is to find where it breaks, so
    // breaching a threshold is a result, not a reason to stop.
    'http_req_duration{expected_response:true}': ['p(95)<1000'],
    'errors_5xx': ['count<1'],
  },
};

export default function () {
  // Two DISTINCT wallets. Same-wallet transfers are rejected by a CHECK constraint
  // (debited_wallet_number <> credited_wallet_number), which would show up as a flood of
  // 400s that has nothing to do with load.
  const a = Math.floor(Math.random() * wallets.length);
  let b = Math.floor(Math.random() * wallets.length);
  if (b === a) b = (a + 1) % wallets.length;

  // Every request needs a unique Idempotency-Key: ledger_idempotency_key_key is UNIQUE,
  // so a repeat is a 409. VU + iteration is unique within a run; RUN keeps runs apart.
  const key = `${RUN}-${__VU}-${__ITER}`;

  const res = http.post(
    `${BASE}/v1/transfer/betweenWallets`,
    JSON.stringify({
      debitedWalletNumber: wallets[a],
      creditedWalletNumber: wallets[b],
      amount: 1,
    }),
    {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
      tags: { name: 'p2p_transfer' },
    }
  );

  check(res, { 'transfer accepted': (r) => r.status === 200 || r.status === 201 });

  if (res.status === 200 || res.status === 201) {
    okLatency.add(res.timings.duration);
  } else if (res.status === 409) {
    conflicts.add(1);
  } else if (res.status === 400) {
    // Distinguish a depleted wallet (invalid run) from other validation failures.
    if (res.body && res.body.includes('nsufficient')) insufficient.add(1);
    else otherErrors.add(1);
  } else if (res.status >= 500 || res.status === 0) {
    serverErrors.add(1);   // status 0 = connection refused / timeout / reset
  } else {
    otherErrors.add(1);
  }
}

export function handleSummary(data) {
  const m = data.metrics;
  const get = (n, f = 'count') => (m[n] && m[n].values ? m[n].values[f] : 0) || 0;

  const lines = [
    '',
    '==================== P2P LOAD TEST ====================',
    `  offered peak rate      : ${MAXRATE} req/s`,
    `  completed requests     : ${get('http_reqs')}`,
    `  achieved throughput    : ${(get('http_reqs', 'rate') || 0).toFixed(1)} req/s (mean over whole run)`,
    `  dropped iterations     : ${get('dropped_iterations')}   <-- k6 could not keep the schedule`,
    '',
    '  latency (successful transfers only)',
    `    p50 : ${(get('latency_successful_transfers', 'med') || 0).toFixed(1)} ms`,
    `    p95 : ${(get('latency_successful_transfers', 'p(95)') || 0).toFixed(1)} ms`,
    `    p99 : ${(get('latency_successful_transfers', 'p(99)') || 0).toFixed(1)} ms`,
    `    max : ${(get('latency_successful_transfers', 'max') || 0).toFixed(1)} ms`,
    '',
    '  failures by kind',
    `    5xx / connection     : ${get('errors_5xx')}      <-- the service breaking`,
    `    409 conflict         : ${get('errors_conflict_409')}      <-- key collision = INVALID RUN`,
    `    400 insufficient     : ${get('errors_insufficient_balance')}      <-- seed too small = INVALID RUN`,
    `    other               : ${get('errors_other')}`,
    '=======================================================',
    '',
  ];
  return { stdout: lines.join('\n') };
}