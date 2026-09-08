# S09 — late settlement: the biller violates the contract

| | |
|---|---|
| **Date** | 2026-09-08 |
| **Correlation prefix** | `S09-` |
| **Roadmap topic** | #7 — **the first genuine threat to the golden rule in ten runs** |

**Why this run exists.** On 3 Sep the settlement window was introduced as a **contract**:
*"the biller settles within N; after that, the payment is void."* That is what makes the
revert safe by **agreement** rather than by guesswork. S09 asks: what happens when the other
side breaks the agreement?

**Mechanism:** raise the simulator's `timeout-sleep-ms` above the 60 s window (3,000 →
90,000), so the biller settles **PAID 30 seconds after the bill has already been reverted**.

```
T+0     pay() -> biller accepts, begins sleeping 90 s
T+2s    caller times out; bill stays Reserved
T+60s   window closes -> sweep inquires -> NOT_FOUND -> REVERT, customer refunded
T+90s   biller finishes and records PAID
        -> customer has their money AND the biller expects payment
```

**Baseline:** 0 `Reserved` · held = 12 · wallet `005100000001` = 16,754.

---

## 2 · PREDICTION — written before the run

| # | Question | Prediction | Confidence |
|---|---|---|---|
| 1 | Is the bill reverted at 60 s, before the biller settles? | **Yes** | medium |
| 2 | What does each side believe at T+90 s? | **bill service: window passed, so refunded. Biller: the agreed window is done, so reject** | high |
| 3 | Does the golden rule break? | **If the biller sticks to the agreement, it holds** | medium |
| 4 | Does anything detect the discrepancy? | **No** | guessing |
| 5 | What does the customer experience? | **Refunded — and the bill paid!** | guessing |

⚠️ **Prediction 2 contains the crux.** It says the biller will *also* honour the window and
reject its own late settlement. But **the simulator settles `PAID` unconditionally after
sleeping** — it has no notion of the window at all. The window exists only in the bill
service's config. **A contract only one side knows about is not a contract**, and this run
tests exactly that.

⚠️ **Prediction 3 depends on prediction 2.** If the biller does not honour the window, the
"if" fails and the question becomes what actually breaks — and *where*.

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
| held in suspense (identity) | 12 | | |
| suspense balance | 12 | | |
| wallet 005100000001 | 16,754 | | |
| drifted wallets | 0 | | |
| **platform vs biller** | — | | **nothing measures this** |

---

## 6 · What I would fix, and whether I fixed it

*(pending)*

---

## 7 · Follow-on scenarios

*(pending)*

---

## 8 · Log evidence

*(pending)*
