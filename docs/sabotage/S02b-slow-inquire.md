# S02b — slow biller on BOTH paths (the sweep can finally be stressed)

| | |
|---|---|
| **Date** | 2026-09-04 |
| **Services** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation prefix** | `S02B-` |
| **Roadmap topic** | #5 — **third attempt to earn or refute the circuit breaker** |

**Why this run exists.** S01 failed to earn the breaker (a dead process refuses connections
in ~1 ms). S02 failed too — `inquire()` was a bare map lookup, so the sweep was immune to
biller latency however hung the payment path was. A scaffolding fix now applies the same
`delayMs` to `inquire`, which is how an overloaded service actually behaves. **Verified
live: an inquire took 3.06 s with `delayMs=3000`, previously instant.**

**Parameters**

```
inquire + pay delay   3000 ms   ← now above the read timeout, so EVERY call times out
read timeout          2000 ms
sweep interval        10 s      fixedDelay, measured from the END of the previous pass
settlement window     60 s
```

**Baseline:** 0 `Reserved` · held = 12 (known orphan) · wallet `005100000001` = 1975.

---

## 1 · What I am breaking

Set the biller to `delayMs = 3000` — above the 2 s read timeout — so **every** call to it
times out: the initial `pay`, and every subsequent `inquire` from the sweep. Submit 5 bills
and leave it running past the 60 s settlement window.

Unlike S01 the biller is alive and accepting connections; unlike S02 the *sweep's* path is
now slow too.

---

## 2 · PREDICTION — written before the run

### Q1 · How long is one sweep pass with 5 stranded bills?

> **10 seconds per pass; unacceptable at around 30 bills.** — *guessing* (carried over
> from S02, where it could not be tested)

### Q2 · Does anything resolve, or is the system stuck until the biller recovers?

> **It will resolve, once the settlement window has passed.** — *medium*

### Q3 · Does the settlement window fire?

> **Yes.** — *medium*

### Q4 · What would a circuit breaker actually save, and is it worth it?

> **"It will save the golden rule."** — *guessing*

⚠️ **Note before running:** Q2 and Q3 are the same claim stated twice — that the window
expires these bills. S01 established that the window lives inside `resolve`, and `resolve`
is only reached when `inquire` *returns*. A timed-out `inquire` throws. **This run tests
whether a timeout counts as an answer.**

**Confidence:** Q1 guessing · Q2 medium · Q3 medium · Q4 guessing.

---

## 3 · What actually happened

```
22:48:43   5 bills submitted, biller delayMs=3000 (above the 2 s read timeout)
22:48:44   5 x HOLD written, all pay() calls time out -> all Reserved

           sweep passes, measured:
           22:51:33.704 ┐
           22:51:35.707 │  2.003 s apart — one read timeout per bill
           22:51:37.710 │
           22:51:39.713 │
           22:51:41.718 ┘  5 bills x 2 s = 10 s per pass
              12 s gap     (10 s fixedDelay, measured from the END of the pass)
           22:51:53.733    next pass

  T+20s .. T+150s   Reserved x5,  held=72   — NOTHING resolved, at 2.5x the window

22:5x      biller restored to delayMs=0
  +12 s    all 5 -> Paid, held back to 12
```

166 failed biller calls logged across the run.

---

## 4 · Prediction vs reality

| Q | Predicted | Actual | |
|---|---|---|---|
| Q1 | **10 s per pass**, unacceptable ~30 bills | **✓ EXACTLY 10 s** — five timeouts of 2.003 s each. And the projection confirms the instinct about 30 | ✓ |
| Q2 | it resolves once the window passes | **✗** — still `Reserved` at T+150 s, 2.5× the window | ✗ |
| Q3 | the settlement window fires | **✗** — never fired | ✗ |
| Q4 | a breaker "will save the golden rule" | **✗** — the golden rule was never at risk (see §5) | ✗ |

**Measured degradation** (pass = N × 2 s, cycle = pass + 10 s):

| bills | pass | cycle | each bill re-checked every |
|---|---|---|---|
| 5 | 10 s | 20 s | 20 s |
| 10 | 20 s | 30 s | 30 s |
| 30 | 60 s | 70 s | 70 s |
| 50 | 100 s | 110 s | 110 s |

### Why Q2/Q3 were wrong — S01's finding, confirmed a second time

A timed-out `inquire` **throws**. The sweep catches it and logs, so `resolve` is never
called and the settlement window is never consulted. **A timeout is not an answer.**

The window can only expire a bill on a pass where the biller *responds* with `NOT_FOUND`.
Confirmed twice now, by two different failure modes (connection refused in S01, read timeout
here): **a bill cannot expire while the biller is unreachable OR unresponsive.** Customer
money stays held for the entire outage, and nothing tells them.

### Why Q4 was wrong — and what the breaker actually protects

The golden rule was never in danger: held = 72, suspense balance = 72, and both returned to
12 after recovery. **Correctness is guaranteed by database constraints, not by timing.** A
circuit breaker cannot protect it and does not need to.

What the breaker actually buys, from the measurements:

1. **Scheduler time** — 10 s of a scheduled thread per cycle, blocked on a dependency
   already known to be failing. Grows linearly with the backlog.
2. **Load off a struggling dependency** — 166 requests were sent to a biller that was
   already too slow to answer. **This is the strongest argument: the breaker protects the
   dependency, not just the caller.** Hammering an overloaded service is how a slow
   dependency becomes a dead one.
3. **Faster recovery detection** — half-open probes cost one call, not N.

What it does **not** buy: resolution. With the breaker open the bills still do not resolve,
because there is still no answer. It makes failing cheap; it does not make it succeed.

---

## 5 · Did the golden rule hold?

| check | before | during | after | agree? |
|---|---|---|---|---|
| held in suspense (identity) | 12 | 72 | 12 | ✓ |
| suspense account balance | 12 | 72 | 12 | ✓ |
| wallet 005100000001 | 1975 | 1915 | 1915 | ✓ (60 correctly paid) |
| drifted wallets | 0 | 0 | 0 | ✓ |

Two independent counts agreed at every point.

---

## 6 · What I would fix, and whether I fixed it

**✅ THE CIRCUIT BREAKER IS FINALLY EARNED — on the third attempt, and not for the reason
originally assumed.**

Measured cost: 10 s of blocked scheduler time per cycle at 5 bills, growing linearly, and
166 calls fired at a dependency already failing. The justification is **load shedding on the
dependency**, with scheduler time second — *not* correctness, which was never at risk.

Placement: the biller client (`inquire` especially, since that is the repeated call).
Half-open probing gives back recovery detection at one call per interval instead of N.

**Still NOT fixed — and the decision is now informed rather than assumed.**

**Second finding, unfixed:** a bill cannot expire while the biller is unresponsive, so
customer money is held for the full outage with nothing told to them. **The breaker does not
help this at all.** It needs either a "hold outstanding > N" alert or a policy that a
sufficiently long unanswered outage expires the bill — which reopens the question the
settlement window was meant to close, since "no answer" and "not found" are different.

---

## 7 · Follow-on scenarios

- **S03 — hold outstanding too long.** How long can money stay held with nobody told?
- **S04 — with the breaker in place**, re-run S02b and measure what actually changed.
  Without a before/after the fix is unproven.
- **S05 — biller slow but under the timeout** (e.g. 1.5 s vs a 2 s timeout). Everything
  "works", but each pass takes N × 1.5 s. Does a breaker even trip? Should it?

---

## 8 · Log evidence

```
one pass, five bills, 2.003 s each:
22:51:33.704 / 35.707 / 37.710 / 39.713 / 41.718   ERROR EODReconciliationJob
                                                    unable to connect to the biller ...
12 s gap, then the next pass at 22:51:53.733

166 failed biller calls logged across the run
recovery: biller -> delayMs 0, all five Paid within 12 s
```
