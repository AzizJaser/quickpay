# S07 — 10,000-bill backlog: where does it actually break?

| | |
|---|---|
| **Date** | 2026-09-06 |
| **Correlation prefix** | `S07-` |
| **Roadmap topic** | #7 — the strongest remaining candidate to earn the batch cap and a separate scheduler thread |

**Why this run exists.** S06 (200 bills) failed to demonstrate a problem: one request body,
0.73 s per pass, relay unaffected. The suspected batch cap has an instinct behind it but no
measurement. **S07 is 50× larger** — at S06's measured 7.6 ms per resolve, a pass should run
past a minute, which is where the sweep's hold on the single scheduler thread stops being
theoretical.

**Setup:** wallet topped up 1,870 → **16,870** (10,000 bills × 1 SAR minimum, since
`amount > 0` is enforced). Request body ≈ **381 KB** of UUIDs, under Tomcat's 2 MB default.

```
sweep interval      10 s fixedDelay      relay interval   2 s (SAME thread)
settlement window   60 s                 measured cost    7.6 ms per resolve
```

---

## 1 · What I am breaking

Strand 10,000 bills at once (biller `SERVER_ERROR`, so every `pay` fails and nothing is
recorded), then let the sweep attempt to reconcile all of them in a single bulk request
followed by 10,000 sequential `resolve` calls on one thread.

---

## 2 · PREDICTION — written before the run

| # | Question | Prediction | Confidence |
|---|---|---|---|
| 1 | Does anything actually break, or does it just get slow? | **It will break** | medium |
| 2 | How long is one sweep pass? | **76 s** | low |
| 3 | What happens to the relay? | **It will be very slow** | medium |
| 4 | Do all 10,000 resolve in one pass? | **No — it will break** | guessing |
| 5 | Does the golden rule hold? | **Yes** | medium |

⚠️ **Note:** predictions 1 and 4 are the same claim — that something fails outright. The
alternative is that it merely degrades. **Every scenario so far has degraded rather than
broken**, so this is the first prediction of an outright failure.

---

## 3 · What actually happened

10,000 bills stranded (2,500 in 16 s, then 7,500 in 45 s). Nothing broke during submission.
The sweep reverted 202 mid-submission as their windows closed, leaving 9,798.

```
pass 1:  2,500 bills in 46.91s  (18.8 ms/bill)   <- competing with submission load
pass 2:  7,405 bills in 38.19s  ( 5.2 ms/bill)
pass 3:     95 bills in  0.56s  ( 6.0 ms/bill)

10,000 RELEASE rows written.  Sweep errors: 0.  Escaped exceptions: 0.
Request body ~381 KB of UUIDs — no limit hit, no rejection, no reset.
```

### 🔴 THE RELAY WAS STOPPED FOR 39.3 SECONDS

```
pass 2:     17:48:18.94  ->  17:48:57.13   (38.19 s)
relay gap:  17:48:17     ->  17:48:56      (39.3 s)   <- near-exact overlap
events created during the gap: 7,186 — all sat unpublished
```

Out of 6,122 relay log lines across the whole run there was **exactly one** gap over 3 s,
and it lines up with pass 2 almost to the second. The relay did not degrade — it **halted**,
because `EODReconciliationJob` and `NotificationPublisherJob` share Spring Boot's default
**single-threaded** `TaskScheduler`.

**7,186 customer notifications were delayed by a biller outage they had nothing to do with.**

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | **it will break** | **✗ nothing broke** — 381 KB body accepted, 10,000 resolved, zero errors | ✗ |
| 2 | 76 s per pass | **~partial** — 46.9 s and 38.2 s across two large passes; a single 10,000 pass would be ~52 s at the measured 5.2 ms/bill. Right order, high by ~50% | ~ |
| 3 | the relay will be very slow | **✓ and worse — it STOPPED for 39.3 s** | ✓ |
| 4 | not one pass, because it breaks | **✗ right conclusion, wrong reason** — three passes, caused by the moving eligibility boundary (S06), not failure | ✗ |
| 5 | the golden rule holds | **✓** — identity 12 = suspense 12, wallet 16,870, **zero drift** across 10,000 concurrent holds | ✓ |

**The system degraded rather than broke — for the eighth time.** Every scenario in Phase 7 has
now bent without snapping. That is itself a finding about the design: the constraints,
idempotency keys and terminal states hold under strain that visibly hurts throughput.

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense account balance | 12 | 12 | ✓ |
| wallet 005100000001 | 16,870 | 16,870 | ✓ (9,799 held, all returned) |
| drifted wallets | 0 | 0 | ✓ |

**10,000 concurrent holds, ~10,000 SAR moved and returned, and both independent counts
agree.** The heaviest strain the ledger has taken, and the invariants did not move.

---

## 6 · What I would fix, and whether I fixed it

### ✅ FIRST EARNED FIX OF PHASE 7 — and it is not a circuit breaker

Seven scenarios produced no justified protection. This one produces a measured, specific
harm: **a 39.3 s total halt of the outbox relay, with 7,186 customer notifications waiting**,
caused by two unrelated jobs sharing one thread.

Two candidate fixes, and they address different halves:

| fix | what it does | what it does NOT do |
|---|---|---|
| **`spring.task.scheduling.pool.size: 2+`** | the relay stops being blocked by the sweep — removes the *coupling* | the sweep still takes 39 s; a slow sweep is still a slow sweep |
| **A batch cap on the sweep** (`findTop500ByStatus...`) | bounds pass duration, so recovery is incremental and any blocking is short | the jobs stay coupled — a big enough cap still starves the relay |

**They are complementary, and the pool size is the more honest fix**: it removes the
*surprising* coupling (bill notifications delayed by biller latency) rather than making the
symptom smaller. The cap is a good second measure, and S06's finding argues for pairing it
with an explicit `ORDER BY` so pass membership is deterministic.

**Also earned, weakly:** S06's suspected batch cap now has *some* evidence — not from a
failed request, but from pass duration. 381 KB and 10,000 references travelled fine; the
cost is time, not size.

**NOT fixed in this session** — recorded so the fix is a decision rather than a reflex.

---

## 7 · Follow-on scenarios

- **S07b — with the pool size raised.** Re-run and confirm the relay gap disappears.
  Without a before/after, the fix is unproven — the same discipline S03 applied to batching.
- **S08 — break a binding.** The measured `publish_in` vs `publish_out` gap.
- **S09 — late settlement.** Violate the settlement window deliberately.

---

## 8 · Log evidence

```
pass boundaries (RELEASE row timestamps):
  pass 1: 17:47:21.918 -> 17:48:08.831   2,500 bills, 46.91 s
  pass 2: 17:48:18.943 -> 17:48:57.133   7,405 bills, 38.19 s
  pass 3: 17:49:07.170 -> 17:49:07.732      95 bills,  0.56 s

relay: 6,122 log lines, exactly ONE gap over 3 s:
  39.3 s starting 17:48:17   <- overlaps pass 2
  7,186 outbox events created during that gap, all unpublished

sweep errors: 0        escaped exceptions: 0        drifted wallets: 0
```
