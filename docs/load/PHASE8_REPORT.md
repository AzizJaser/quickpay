# Phase 8 — load test: finding the breaking TPS on P2P

> Prediction must be written and saved **before** the run. The plan attached this rule to
> Phase 8 explicitly: *"before k6 fires, write the predicted breaking TPS **and** the
> predicted first bottleneck."*

| | |
|---|---|
| **Date** | 2026-09-10 |
| **Target** | 500 TPS on P2P (non-functional requirement from the plan) |
| **Definition of done** | *"The load report names the breaking TPS on P2P and at least one fix that moved it."* |
| **Harness** | [`scaffolding/load-test/`](../../scaffolding/load-test/) |

---

## 1 · Setup

**Containerised, not run from the IDE.** IntelliJ attaches `-javaagent:idea_rt.jar` and runs
with `-XX:TieredStopAtLevel=1`, capping JIT at C1. A throughput number measured that way is
not the service's number.

**Explicit CPU budget**, so the result is reproducible and comparable before and after a fix:

```
wallet-service   2.0 CPU   1g        <- the system under test
postgres         2.0 CPU   1g
rabbitmq         1.0 CPU   512m
host: 8 cores, 16 GiB — k6 runs natively, outside the Docker VM
```

**Configuration under test — both Spring defaults, neither set in `application.yml`:**

```
Hikari  spring.datasource.hikari.maximum-pool-size   10   (default)
Tomcat  server.tomcat.threads.max                   200   (default)
```

**Load profile:** `ramping-arrival-rate`, 50 → 200 → 400 → 600 → 900 → 1600 req/s over
~3½ minutes. Arrival rate rather than virtual users, because VUs *wait* when a service slows
— offered load would fall with throughput and the system would never appear saturated.

**Workload:** P2P transfer of 1 SAR between two distinct wallets drawn at random from a pool
of 200 funded wallets (39,800 ordered pairs), unique `Idempotency-Key` per request.

---

## 2 · PREDICTION  ⚠️ written before the run

### Learner's predictions, verbatim

| Q | Prediction | Confidence |
|---|---|---|
| **Q1 · breaking TPS** | *"20ms"* connection hold time → **500 TPS** by Little's Law | **guessing** |
| **Q2 · first bottleneck** | *"multiple request will not have connection since it's limited to 10"* — **connection-pool exhaustion** | stated as reasoning, not hedged |
| **Q3 · hot row: P2P or top-up?** | *"same"* — neither flow holds a hotter row than the other | **guessing** |

### How Q1 was derived

Little's Law: `throughput = concurrency ÷ latency`. The pool caps concurrency at **10**
connections, so the whole question reduces to how long one transfer holds one connection.
The estimate given was **20 ms** — two `SELECT … FOR UPDATE`s, two balance updates, a ledger
insert, and up to two outbox inserts.

```
10 connections / 0.020 s = 500 TPS
```

⚠️ **Noted before the run:** this lands exactly on the 500 TPS target by coincidence, not by
design. The two numbers were arrived at independently — the target is a business requirement
from the plan; 500 here is the output of a latency guess.

### Q3 is the sharp one

The plan reserved this question for Phase 8 specifically: *"which flow holds a hot row under
load — P2P or top-up — and why?"* The answer **"same"** is falsifiable, which is what makes
it worth recording. The place to look is what each flow locks and across how many distinct
rows those locks can spread.

`topup.js` is identical to `p2p.js` in executor, stages and rates — the only difference is the
endpoint — so any gap between the two numbers is attributable to the flow, not the method.

---

### E1 prediction — raising the Hikari pool 10 → 50 ⚠️ written before the run

Learner, verbatim: *"nothing will change, it's not the bottleneck. the db matched the app"*

A direct test of Q2. If the pool were the constraint, 5× the connections would move the
ceiling substantially. The supporting observation — that wallet-service and wallet-db pinned
their 200% CPU limits **together** — is the reason to expect nothing.

### E2 prediction — removing the `existsByIdempotencyKey` SELECT ⚠️ written before the run

Learner, verbatim: *"it will move a little - 5% - it's very light weghited check"* — **medium**.

Rationale for the experiment: S12 measured this check catching **0 of 10** duplicates in a
race while `ledger_idempotency_key_key` caught 9 of 9. It is a latency optimisation on the
sequential path, not a correctness guarantee — and on a path where both tiers are CPU-pinned,
it is one statement per request that Postgres will perform again anyway as a constraint check.

### E3 prediction — doubling the CPU budget (2 → 4 per tier) ⚠️ written before the run

Learner, verbatim: *"2000 - medium, k6 will compete for the cores"*

Sub-linear on purpose. Linear scaling from ~1600 would be ~3200; the prediction is ~2000
because 4 + 4 + 1 = **9 CPU allocated on an 8-core host**, with k6 running natively alongside
and needing cores of its own to generate 5000 req/s. The mechanism is named, which is what
makes it falsifiable.

This is the experiment that CONFIRMS or BREAKS the CPU diagnosis. If the ceiling moves
roughly with the budget, the bottleneck is established by experiment rather than inferred
from two containers pegging their limits together. If it does not move, the real constraint
is somewhere neither of us has looked — and that would be the finding.

### E4 prediction — 4 CPU **and** pool 50 ⚠️ written before the run

Learner, verbatim: *"2500 - medium, the pool was the wall"*

The experiment E1 could not run. At 2 CPUs the pool was not binding (6 of 10 active) and
raising it to 50 made throughput *worse*, because 41 backends fought over 2 cores. At 4 CPUs
neither tier is CPU-saturated (61% and 73% of budget) and **9 of 10 connections are busy** —
so the same change is now testing a completely different hypothesis.

From 1716 req/s sustained. A prediction of 2500 is +46%.

---

## 3 · What actually happened

Four runs, identical ramp each time (50 → 200 → 400 → 600 → 900 → 5000 req/s over 3m30s).
Stages 1–5 offer 51,250 requests and were completed in full every time, so **sustained
throughput is measured over the top 70 seconds**, where the system is actually saturated.

| config | sustained | vs base | p50 | p95 | p99 | what was pinned |
|---|---|---|---|---|---|---|
| **2 CPU, pool 10** *(as shipped)* | **1560/s** | — | 361 ms | 985 ms | 1259 ms | **both tiers**, 213% and 212% of 200% |
| 2 CPU, pool 50 | 1384/s | **−11.3%** | 441 ms | 1076 ms | 1368 ms | both tiers still; 41 backends over 2 cores |
| 4 CPU, pool 10 | 1716/s | +10.0% | 406 ms | 837 ms | 1096 ms | **the pool** — 9 of 10 active |
| **4 CPU, pool 50** | **1867/s** | **+19.7%** | **340 ms** | **775 ms** | **1024 ms** | **the database**, 393% of 400% |

**Zero errors in every run.** No 5xx, no connection failures, no 409s, no insufficient-balance
400s across ~929,000 transfers. The service never failed — it got slow.

### The bottleneck moved three times

```
2 CPU          app CPU + db CPU both pinned          1560/s
  |  +2 CPU each
4 CPU          neither pinned; pool at 9 of 10       1716/s
  |  +40 connections
4 CPU pool 50  db CPU pinned at 98%; app at 58%      1867/s
```

Every time a constraint was relieved, a different one appeared. This is the S07b pattern from
Phase 7 — fixing the scheduler blocking revealed a 50 events/sec relay ceiling that had been
invisible behind it — reproduced on a completely different axis.

**The database is the floor.** At every configuration where it is allowed to saturate, it does,
while `wallet-service` sits at 58% of its budget. The host is also near its limit: peak
combined container CPU was **6.0 of 8 cores**, leaving ~2 for k6 and macOS.

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| **Q1** breaking TPS | **500** | **~1560/s** at the shipped config | ✗ **3.1× low** |
| **Q2** first bottleneck | connection-pool exhaustion | **CPU on both tiers** — pool was idle (6 of 10 active, 0 lock waits) | ✗ *then* ✓ — see below |
| **Q3** hot row: P2P or top-up | *"same"* | **top-up**, decisively — sustained `Lock:tuple x5–8` vs ~0 for P2P | ✗ |
| **E1** pool 10→50 at 2 CPU | *"nothing will change, it's not the bottleneck"* | **−11.3%** — no improvement, measurably worse | ✓ |
| **E3** double CPU | 2000/s | 1716/s | ✗ direction right, magnitude low |
| **E4** 4 CPU + pool 50 | 2500/s | 1867/s | ✗ |

### Why Q1 was 3× low — the method was right, one input was wrong

Little's Law was applied correctly: `10 connections ÷ 20 ms = 500 TPS`. The error was the
**20 ms**. Measured p50 at low load is **1.8 ms end-to-end HTTP**, so a connection is held
~1 ms. That puts the *pool's* ceiling near 10,000 TPS — an order of magnitude above where the
real limit sits. **The formula found the wrong bottleneck because the estimate fed into it
was 20× too pessimistic.**

### 🟢 Q2 is the interesting one — wrong, then right

At the shipped configuration the pool was **not** the constraint: 6 of 10 connections active,
zero lock waits, both tiers pinned at their CPU limit. Q2 was falsified.

But at 4 CPUs, with the CPU relieved, **the same pool became the wall** — 9 of 10 active — and
raising it to 50 then bought +8.8%. Q2 described a real constraint that was simply *second in
line*.

⚠️ **And E1 proves why that distinction matters.** The fix Q2 implied, applied at the shipped
configuration, made throughput **11% worse** and drove lock waits from 1 to 26. On a CPU-bound
database a bigger pool queues work inside Postgres instead of inside Hikari, and queueing
deeper never makes a saturated resource faster.

**Had the diagnosis stopped at "pool exhausted, raise the pool", the result would have been a
regression with a plausible story attached.** The same change was right later and wrong then.

### Q3 — measured, not argued

```
                   P2P      TOP-UP
completed      160,434    119,132   −26%
p50 latency      361 ms     841 ms   2.33×
max lock_waits        1          9
```

Probe output during top-up, sustained for the whole high-load phase:

```
Lock:tuple x7, Lock:transactionid x1, running:- x1
Lock:tuple x8, Lock:transactionid x1, running:- x1
```

`Lock:tuple` is a backend waiting on a **row** lock. Every top-up debits internal wallet
`000000000001`, so all of them serialise on that one row. P2P spreads across 200 wallets —
39,800 ordered pairs — so collisions are rare. **Top-up holds the hot row; P2P does not.**

---

## 5 · The bottleneck, named

**The database, on CPU.** `wallet-db` saturates first at every configuration that permits it,
while the application sits at ~58% of its budget. The pool and the application CPU are both
real constraints, but they are second and third in line.

The per-request database work is five writes and three reads: two `SELECT … FOR UPDATE`, two
balance `UPDATE`s, one ledger `INSERT`, up to two outbox `INSERT`s, and one redundant
`existsByIdempotencyKey` `SELECT`.

---

## 6 · The fix, and whether it moved the number

**+19.7%** — 1560 → 1867 req/s sustained, with every latency percentile improving to its best
of the phase. Two changes, both configuration, neither touching application code:

| change | effect | why |
|---|---|---|
| CPU 2 → 4 per tier | +10.0% | both tiers were pinned |
| pool 10 → 50 *(at 4 CPU)* | +8.8% | pool became the wall once CPU was relieved |
| pool 10 → 50 *(at 2 CPU)* | **−11.3%** | ⚠️ the same change, applied to the wrong constraint |

Both knobs are now parameterised in `docker-compose.load.yml` (`CPU_LIMIT`, `HIKARI_POOL`), so
this is reproducible rather than a one-off.

### 🔴 The honest headline: this optimisation was not earned

**The requirement is 500 TPS. The shipped configuration delivers ~1560 — 3.1× over.**

Phase 7 spent five scenarios refusing to build a circuit breaker because no measurement
justified it. The same rule applies here: a system exceeding its target 3.1× does not need
tuning, and the +19.7% above is a *diagnostic* exercise, not a shipped improvement. The CPU and
pool values used to obtain it are load-test knobs; **nothing in `application.yml` was changed,
deliberately.**

**NOT DONE — `existsByIdempotencyKey` removal.** S12 measured this check catching 0 of 10
duplicates in a race while the UNIQUE constraint caught 9 of 9. Removing it saves one statement
per request. It was **not** attempted, and the reason is a review finding rather than
reluctance: `GatewayWebhookController` explicitly catches `DuplicatedEntryException` to return
200 so the gateway stops redelivering. Remove the pre-check and the duplicate surfaces as
`DataIntegrityViolationException`, which that catch does not match — the webhook would 500 and
**the gateway would retry forever.** Two integration tests also assert the old exception type.
It is a three-file change on a money path, and it should be justified by API consistency
(S12 found the two duplicate paths return different bodies under the same 409), not by ~5%
on a number already 3× over target.

---

## 7 · Did the golden rule hold?

**Yes — under ~929,000 transfers at up to 1867/s.**

| check | after |
|---|---|
| ledger rows | 970,358 |
| drifted wallets | **0** |
| sum of ALL wallet balances | **0** |
| ledger nets to | **0** |
| duplicate idempotency keys | **0** |
| held in suspense / suspense balance | 12 / 12 — unchanged since Phase 7 |

Not one duplicate key across ~929,000 concurrent inserts, and not one SAR created or destroyed.
S12 established that the DB constraints, not the code checks, are what guarantee this; Phase 8
ran that guarantee at ~1800 requests per second for fourteen minutes.

---

## 8 · Evidence

```
CPU, 2-CPU limit (200%):     wallet-service peak 213%   wallet-db peak 212%    BOTH PINNED
CPU, 4-CPU limit (400%):     wallet-service peak 246%   wallet-db peak 291%    neither pinned
CPU, 4-CPU + pool 50:        wallet-service peak 232%   wallet-db peak 393%    DB PINNED (98%)

pool 10 @ 2 CPU:   max active  6 of 10   max lock_waits  1
pool 50 @ 2 CPU:   max active 41 of 50   max lock_waits 26     <- 26x contention, -11% throughput
pool 10 @ 4 CPU:   max active  9 of 10   max lock_waits  1     <- pool is now the wall
pool 50 @ 4 CPU:   max active 50 of 50   max lock_waits 25

host: peak combined container CPU 598% = 6.0 of 8 cores, ~2 left for k6 and macOS
```

### Findings outside the load test

**🔴 `WalletController` path variables are broken — all four endpoints.** Confirmed live while
bringing the container up:

```
MissingPathVariableException: Required URI template variable 'wallet_number'
  for method parameter type String is not present
```

Every mapping declares `{walletNumber}` in the URI template and binds
`@PathVariable String wallet_number`. `GET /v1/wallets/{id}`, `activate`, `suspend` and `close`
all return **500**. The generic `Exception` handler converts it to
*"An unexpected error occurred"*, so a caller sees a server error with no indication of cause
and the real reason exists only in the logs. **Not fixed — application code.**

### Harness bugs found and fixed during the phase

- `probe.sh` — `group by 1` on an expression containing `count(*)`; the first P2P run had no
  DB telemetry at all.
- `p2p.js` / `topup.js` — k6's default `Trend` stats stop at p(95), so p99 reported as `0.0`
  until `summaryTrendStats` was set explicitly.
