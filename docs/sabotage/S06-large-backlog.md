# S06 — large backlog: the request size nobody bounded

| | |
|---|---|
| **Date** | 2026-09-06 |
| **Correlation prefix** | `S06-` |
| **Roadmap topic** | #7 — and it tests the gap S05 found while predicting |

**Why this run exists.** S03's batching made the *call count* independent of the backlog.
But `billRepository.findByStatus(Reserved)` has **no limit**, so the request **size** is
still proportional to it — every stranded bill goes into one POST body. The notification
retry job was deliberately bounded (`findTop100By...`); the sweep never was.

**Baseline:** 0 `Reserved` · 60 bills total · held = 12 (known orphan) ·
wallet `005100000001` = 1870.

---

## 1 · What I am breaking

Strand ~200 bills at once (biller unreachable, so every `pay` fails), then restore the
biller and watch a single bulk request carry all 200 references — an ~8 KB body of 36-char
UUIDs — followed by 200 sequential `resolve` calls on the single scheduler thread.

---

## 2 · PREDICTION — written before the run

| # | Question | Prediction | Confidence |
|---|---|---|---|
| 1 | Does the bulk request succeed with 200 references? | **No** | medium |
| 2 | How long is one sweep pass at 200 bills? | **~5 seconds** | guessing |
| 3 | What happens to the relay while the sweep holds `scheduling-1`? | **Nothing** | medium |
| 4 | Does the response come back intact, all 200 correctly joined? | **Not necessarily** | guessing |
| 5 | Does the batch cap need to exist? | **Yes — and the limit should be an agreement per the contract, not a number picked unilaterally. Also: the sweep should run on its own thread.** | high |

⚠️ **Note on prediction 5** — a batch limit as a *negotiated contract* is the
settlement-window lesson applied a second time: what makes a bound safe is **agreement with
the other side**, not a value chosen locally.

⚠️ **Predictions 1 and 3 are in tension.** If the request fails outright (1), the sweep would
be fast and the relay unaffected (3) — but for a different reason than "the load is fine".
The run distinguishes them.

**Cost model given before the run** (to make Q2 answerable rather than blind):
`pass ≈ one bulk call (fixed, ~delayMs) + N × resolve()`, where each `resolve` for a
`NOT_FOUND` past the window does one wallet HTTP call plus two DB writes. **At 200 bills the
biller is no longer the cost — the resolve loop is.** S03 removed the scaling from one place
and it reappears in another.

---

## 3 · What actually happened

200 bills stranded in 1.38 s (10:22:14.81 → 10:22:16.19), 200 SAR held. All 200 resolved.

```
pass 1:   93 bills in 0.73 s
gap:      10.04 s            <- fixedDelay between passes
pass 2:  107 bills in 0.70 s

~7.6 ms per bill.  Bulk request carried all references in ONE body — no limit hit.
200 of 200 correctly joined.  Zero sweep errors.
```

*(The 200 ERROR lines in the log are the original `pay` calls receiving 5xx — the mechanism
used to strand the bills, not sweep failures.)*

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | the bulk request will **not** succeed at 200 refs | **✗** — succeeded, ~8 KB body, no limit reached | ✗ |
| 2 | ~5 s per pass | **✗ 0.73 s** — 7× faster than predicted | ✗ |
| 3 | nothing happens to the relay | **✓** — relay ran at 10:23:15.7, between passes, unblocked | ✓ |
| 4 | response not necessarily intact | **✗** — 200 of 200, all correctly joined | ✗ |
| 5 | a cap is needed | **unresolved — the run gave no evidence for one.** See §6 | ~ |

### 🔴 SURPRISE — the eligibility boundary MOVES during a pass

The split into 93 + 107 was not a batch limit and not the settlement window at pass start.
It is subtler:

```
eligible at pass START (created < 10:22:14.968):   40
eligible at pass END   (created < 10:22:15.700):  120
actually reverted:                                 93   <- between the two
```

`resolve` evaluates `LocalDateTime.now()` **per bill**, so as the sweep works through the
results the 60-second cutoff **slides forward with it**. Bills that were not eligible when
the pass began became eligible by the time the loop reached them. With 200 bills created in
1.38 s, that sliding boundary swept up ~53 extra bills mid-pass.

**And which bills is arbitrary.** `findByStatus(Reserved)` has no `ORDER BY`, so results
return in whatever order Postgres gives them. Two bills created 1 ms apart can land in
different passes purely on iteration order.

Not a correctness problem — every bill reverted, the golden rule held, and the stragglers
were caught 10 s later. But **the split is non-deterministic**, which matters when trying to
reason about a sweep's behaviour from its output. The notification retry job avoids this by
ordering explicitly (`...OrderByCreatedAtAsc`); the bill sweep does not.

### Why prediction 2 was 7× out — and why my own first figure was wrong too

The cost model given before the run was right in shape but wrong in magnitude: a `resolve`
costs ~7.6 ms, not the ~25 ms assumed. **I also mis-measured it initially at 57.7 ms** by
dividing 200 bills across the full 11.47 s span — which included the 10 s idle gap between
passes. Dividing by elapsed wall-clock rather than by working time is an easy way to
overstate a per-item cost by an order of magnitude.

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense account balance | 12 | 12 | ✓ |
| wallet 005100000001 | 1870 | 1870 | ✓ (200 held then fully returned) |

200 SAR held and returned in full, two independent counts agreeing.

---

## 6 · What I would fix, and whether I fixed it

**NOTHING FIXED — and prediction 5's cap now has no evidence behind it.**

The unbounded `findByStatus(Reserved)` was flagged in S05 as a gap. **This run failed to
demonstrate it is a problem**: 200 references travelled in one body without hitting any
limit, the pass took 0.73 s, and the relay was unaffected. **A cap remains a reasonable
instinct with no measurement supporting it** — the same position the circuit breaker was in
after S01, and the honest thing is to say so rather than build it.

Where it *would* start to matter, for a future run: default Tomcat `maxHttpFormPostSize`
(2 MB) is ~50,000 UUIDs away; the pass duration grows linearly at ~7.6 ms/bill, so 10,000
stranded bills would be a 76 s pass — long enough to starve the relay properly and worth
testing before assuming.

**Better supported by this run: an explicit `ORDER BY` on the sweep query.** Not for
performance, but so the sweep's behaviour is deterministic and reproducible. Costs nothing,
and the notification retry job already does it. Still not fixed — recorded as a candidate.

**Prediction 5's other half — "the sweep should run on its own thread" — is untested here.**
The relay was unaffected at 0.73 s. It would matter at a much larger backlog, which is the
scenario above.

---

## 7 · Follow-on scenarios

- **S07 — 10,000 stranded bills.** Where the linear resolve cost genuinely bites, and the
  first scenario that could earn both the cap and the separate thread.
- **S08 — break a binding.** The measured `publish_in` vs `publish_out` gap.
- **S09 — late settlement.** Violate the settlement window deliberately.

---

## 8 · Log evidence

```
pass boundaries, from RELEASE row timestamps:
  10:23:14.968  first release   ┐ pass 1: 93 bills
  10:23:15.700  last release    ┘ 0.73 s
  10:23:25.741  next release    <- 10.04 s gap = fixedDelay
  10:23:26.442  last release      pass 2: 107 bills, 0.70 s

relay ran BETWEEN passes, unblocked:
  10:23:15.708 [scheduling-1] NotificationPublisherJob

200 ERROR lines = the original pay() 5xx used to strand the bills, not sweep failures.
```
