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
| wallet 005100000001 | 16870 | | |

---

## 6 · What I would fix, and whether I fixed it

*(pending)*

---

## 7 · Follow-on scenarios

*(pending)*

---

## 8 · Log evidence

*(pending)*
