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

---

## 3 · What actually happened

*(pending)*

## 4 · Prediction vs reality

*(pending)*

## 5 · The bottleneck, named

*(pending)*

## 6 · The fix, and whether it moved the number

*(pending)*

## 7 · Did the golden rule hold?

*(pending)*

## 8 · Evidence

*(pending)*