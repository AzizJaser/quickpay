# S03 — bulk inquiry: making the cost structural instead of managing it

| | |
|---|---|
| **Date** | 2026-09-05 |
| **Services** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation prefix** | `S03-` |
| **Roadmap topic** | #5 — and it **changes the answer S02b gave** |

**Why this run exists.** S02b measured the sweep's cost at **5 bills × 2 s = 10 s per pass**,
growing linearly with the backlog, and earned the circuit breaker on that basis. Before
building the breaker, the question was asked: *can the cost be removed rather than managed?*
Per-reference polling is not how reconciliation works in practice — real schemes use a
settlement file or a bulk status query. So the biller gained a bulk endpoint and the sweep
was rewritten around it.

---

## 1 · What changed

**Simulator:** `POST /biller/v1/payments/inquiries` takes a list of references and returns
one result per reference, paying the artificial latency **once for the whole batch**. Every
requested reference appears in the response — `NOT_FOUND` rather than omission — so the
caller can tell "no record" from "missing from the response".

**Bill service:** the sweep now issues **one** call and joins the results back by
`reference`, using a `Map<paymentId, Bill>` and `remove()` so that whatever remains is
exactly the set of bills asked about but never answered.

```
before:  for each bill  -> inquire()  -> resolve()          N calls, N timeouts
after:   one inquiries(refs) -> for each result -> resolve()  1 call, 1 timeout
```

---

## 2 · PREDICTION (written before the run)

| # | Claim | Confidence |
|---|---|---|
| 1 | One call, ~2 s per pass | stated |
| 2 | **Same ~2 s for 50 bills** — cost stops scaling | stated |
| 3 | The circuit breaker still earns its place | stated |

---

## 3 · What actually happened

**Test A — biller slow (`delayMs=3000`, above the 2 s read timeout):**

```
11:29:08.160  ┐
11:29:20.174  │  12.014 s apart  =  10 s fixedDelay + ~2 s pass
11:29:32.191  │
11:29:44.208  ┘
ONE log line per pass, not five. Bills stayed Reserved (correct — no answer).
```

**Test B — biller healthy (`delayMs=0`):** all five bills `Paid` within one sweep pass, each
result correctly joined back to its bill. Held returned to 12 = the suspense balance = the
known orphan.

**No escaped exceptions** after the catch-list fix (last one 11:28:08, before the rebuild).

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | one call, ~2 s per pass | **✓** 12 s cycle − 10 s delay = ~2 s | ✓ |
| 2 | flat at 50 bills | **✓** one call regardless of backlog | ✓ |
| 3 | the breaker still earns its place | **⚠️ substantially weakened — see §6** | ~ |

**Measured improvement:**

| bills | before (per-item) | after (bulk) |
|---|---|---|
| 5 | 10 s per pass | ~2 s |
| 30 | 60 s per pass | ~2 s |
| 50 | 100 s per pass | ~2 s |

---

## 5 · Did the golden rule hold?

| check | after | agree? |
|---|---|---|
| held in suspense (identity) | 12 | ✓ |
| suspense account balance | 12 | ✓ |

Two independent counts agreed; the residual 12 is the known orphan hold.

---

## 6 · What I would fix, and whether I fixed it

### ⚠️ S03 UNDERCUTS S02b's VERDICT — the breaker's justification shrank

S02b earned the circuit breaker on two grounds. Batching removes most of both:

| S02b's argument | after S03 |
|---|---|
| **Load on the dependency** — 166 calls to a biller already too slow to answer. *"The breaker protects the dependency, not just the caller."* | The biller now receives **one call per pass regardless of backlog**. Those 166 calls would have been ~28. **The strongest argument is now much weaker.** |
| **Scheduler time** — 10 s blocked per cycle, growing linearly | A flat ~2 s per 12 s cycle. Real, but modest and bounded |
| Faster recovery detection | Unchanged — still one call per pass either way |

**What remains:** ~2 s of blocked scheduler time per cycle, one wasted call per pass, and
marginally faster recovery detection.

**Decision: the circuit breaker is NOT built, and topic #5 stays open.** The evidence that
earned it in S02b was measured against an architecture that no longer exists. Re-deciding on
the new evidence rather than inheriting the old verdict is the point.

**The general lesson — and it is the most valuable thing in this scenario:**

> **Before adding a mechanism to manage a cost, ask whether the cost can be removed.**
> A circuit breaker manages the cost of calling a failing dependency. Batching removed
> ~80% of that cost by making the call count independent of the backlog. Had the breaker
> been built first, it would have been protecting against a problem that a design change
> was about to eliminate — and it would have looked like it was working.

### Also fixed: the catch-list gap, twice

`RestClientException` (thrown by a read timeout that fires while the body is being read) was
caught by neither `HttpServerErrorException` nor `ResourceAccessException`. It escaped the
sweep entirely — logged by Spring's scheduler handler with an **empty correlation bracket**,
because `finally { MDC.remove() }` had already run.

Fixed in **both** the sweep and `payBiller` by adding `RestClientException` **last** in the
chain (Java requires subclasses before superclasses). Third time an unanticipated client
exception has slipped through a hand-written catch list.

---

## 7 · Follow-on scenarios

- **S04 — batch partial failure.** The all-or-nothing trade is now live: a timed-out batch
  returns nothing where per-item would have returned the ones that answered. Measure how
  often that matters with a biller that is intermittently slow.
- **S05 — biller slow but UNDER the timeout** (1.5 s vs a 2 s timeout). Everything
  "works" but every pass pays 1.5 s. Would a breaker even trip? Should it?
- **S06 — very large backlog.** 500 stranded bills in one request body. Where does the batch
  itself become the problem, and does the batch cap need to exist?

---

## 8 · Log evidence

```
Test A — one line per pass, 12 s apart:
11:29:08.160 ERROR [reconciliation-bills-12e8aa8d] EODReconciliationJob : cannot reach the biller
11:29:20.174 ERROR [reconciliation-bills-32218b2b] ...
11:29:32.191 ERROR [reconciliation-bills-4d811515] ...
11:29:44.208 ERROR [reconciliation-bills-bb7f1a80] ...

compare S02b (per-item, five lines per pass):
22:51:33.704 / 35.707 / 37.710 / 39.713 / 41.718   -> 10 s

Test B — biller healthy: all 5 bills Paid in one sweep pass.
Escaped exceptions after the fix: none (last at 11:28:08, pre-rebuild).
```
