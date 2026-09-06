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

*(pending)*

---

## 4 · Prediction vs reality

*(pending)*

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense | 12 | | |
| suspense balance | 12 | | |
| wallet 005100000001 | 1870 | | |

---

## 6 · What I would fix, and whether I fixed it

*(pending)*

---

## 7 · Follow-on scenarios

*(pending)*

---

## 8 · Log evidence

*(pending)*
