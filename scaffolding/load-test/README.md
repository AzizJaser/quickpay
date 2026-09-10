# Phase 8 — load test

Finds the breaking TPS on P2P transfer, names the bottleneck, and measures whether a fix
moved it. Everything here is test infrastructure; no service code is touched.

## Why it is set up this way

**Containers, not the IDE.** IntelliJ attaches `-javaagent:idea_rt.jar` and runs with
`-XX:TieredStopAtLevel=1`, capping JIT at C1. A throughput number measured that way is not
the service's number.

**Explicit CPU limits** (`docker-compose.load.yml`): wallet-service 2.0, Postgres 2.0,
RabbitMQ 1.0, on an 8-core host with k6 running natively alongside. A breaking TPS measured
on "whatever cores were free" cannot be compared before and after a fix — and Phase 8's
definition of done requires exactly that comparison.

**`ramping-arrival-rate`, not `ramping-vus`.** Virtual users wait when a service slows, so
offered load falls with throughput and the system never appears saturated. Arrival rate
targets a request rate regardless of latency; when the service cannot keep up, k6 reports
`dropped_iterations`. That is the breaking point, made visible.

**Failures are split by kind.** A 409 means idempotency keys collided and the run is
invalid. A 400 "insufficient balance" means the seed was too small and the run is invalid.
Only 5xx and connection failures are the service actually breaking. One blended error rate
would hide which happened.

## Running it

```bash
docker compose -f docker-compose.yml -f docker-compose.load.yml up -d --build
./scaffolding/load-test/seed.sh 200            # ~200 funded wallets -> wallets.json
./scaffolding/load-test/probe.sh 220 5 &       # sample the DB during the run
k6 run scaffolding/load-test/p2p.js
k6 run scaffolding/load-test/topup.js          # the comparison
```

`seed.sh` funds through the real top-up endpoint, never by `UPDATE`ing balances — a direct
balance write would create money with no ledger row and poison the golden-rule check that
every Phase 7 scenario depends on.

## Reading the result

| symptom | means |
|---|---|
| throughput flattens, p95 climbs, `active` connections pinned at the Hikari max | the connection pool is the cap |
| `wait_event_type = 'Lock'` climbing | row contention — some row is hot |
| `wait_event_type = 'Client'` dominant | Postgres is idle; the constraint is upstream of it |
| `dropped_iterations` rising | k6 could not start iterations on schedule — the service is behind |
| 409s appearing | key collision, invalid run |
| 400 insufficient | seed too small, invalid run |

## Files

| file | what |
|---|---|
| `seed.sh` | creates and funds the wallet pool, writes `wallets.json` |
| `p2p.js` | the primary test — P2P transfer, ramping arrival rate |
| `topup.js` | identical shape against top-up, for the hot-row comparison |
| `probe.sh` | samples `pg_stat_activity` during a run |
