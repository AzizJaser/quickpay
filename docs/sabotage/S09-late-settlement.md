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

```
T+0     bill submitted, biller in TIMEOUT (sleeps 90 s, then settles PAID)
T+20 s  Reserved   wallet 16,677  (77 held)      biller: nothing settled
T+45 s  Reserved   wallet 16,677                 biller: nothing settled
T+70 s  Rejected   wallet 16,754  <- REFUNDED    biller: nothing settled
T+100 s Rejected   wallet 16,754                 biller: 1 SETTLED, status PAID
```

**The two sides believe opposite things about the same payment:**

```
biller:        ref 2bbc49d5...  bill S09-001  status PAID     txn BILR-dad9e431...
bill service:                   bill S09-001  status Rejected  pid 2bbc49d5...
```

Ledger: `HOLD 77` at 08:48:45 → `RELEASE 77` at 08:49:50. Customer notified twice —
`bill.payment.rejected` and `wallet.money.received`.

---

## 4 · Prediction vs reality

| # | Predicted | Actual | |
|---|---|---|---|
| 1 | reverted at 60 s, before the biller settles | **✓** — Rejected by T+70, biller settled at T+90 | ✓ |
| 2 | bill refunds; **biller also rejects** because the window passed | **half wrong** — the bill service refunded ✓, but the biller settled **PAID** ✗ | ~ |
| 3 | golden rule holds *if the biller sticks to the agreement* | **the biller did NOT stick to it — and the golden rule held anyway.** See below | ~ |
| 4 | nothing detects the discrepancy | **✓** — nothing anywhere | ✓ |
| 5 | customer refunded **and** the bill paid | **✓ exactly** | ✓ |

### 🔴 THE FINDING — the golden rule measures internal consistency, not solvency

**Both counts still agree: identity 12 = suspense 12, zero wallet drift.**

That is not luck. The ledger is *perfectly* balanced: 77 in, 77 out. Nothing was created or
destroyed **inside the wallet**. The invariants did exactly what they were built to do.

**But the platform now owes the biller 77 SAR, and no record of that debt exists anywhere.**

```
customer:      refunded, told twice, view is coherent
bill service:  Rejected — believes the payment failed
biller:        PAID — expects 77 SAR
wallet:        balanced, zero drift
```

Every individual view is self-consistent. **The inconsistency exists only in the space
BETWEEN the systems, and nothing measures that space.**

Ten scenarios have hammered the golden rule without moving it, and this run explains why:
`SUM(HOLD) − SUM(SETTLEMENT) − SUM(RELEASE)` and the suspense balance are **both derived
from the wallet's own ledger**. Two counts of the same book. They can never disagree about
money the book does not know about.

**A "second independent count" that shares a source is not independent.** The only genuinely
external check would compare the platform's records against the *biller's* — which is what
real settlement reconciliation is, and why it exists.

### Why prediction 2 was half wrong — a contract only one side knows

The prediction assumed the biller would also honour the window and reject its own late
settlement. It cannot: **`biller-settlement-window-ms` lives only in the bill service's
config.** The simulator sleeps and then settles `PAID` unconditionally; it has never heard of
the window.

**A contract only one side knows about is not a contract — it is an assumption.** The
settlement window was introduced (3 Sep) precisely to replace a guess with an agreement, and
S09 shows the agreement was never actually made. It is a unilateral timeout wearing a
contract's clothes.

---

## 5 · Did the golden rule hold?

| check | before | after | agree? |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense account balance | 12 | 12 | ✓ |
| wallet 005100000001 | 16,754 | 16,754 | ✓ |
| drifted wallets | 0 | 0 | ✓ |
| **platform vs biller** | — | **−77 SAR** | ❌ **nothing measures this** |

**The golden rule held and money was still lost.** Both facts are true, and the gap between
them is the scenario's entire content.

---

## 6 · What I would fix, and whether I fixed it

**NOT FIXED — and this is the deepest finding of Phase 7, because it is about what the
invariants can see rather than about a bug.**

**1. Make the window a real contract, not an assumption.** Today it is one number in one
service's config. A genuine agreement means the biller enforces it too — refusing to settle
after the window, and answering `EXPIRED` rather than settling late. That is a scaffolding
change *and* a conversation with a real biller. **Until then, every revert carries this risk;
the window narrows it, it does not remove it.**

**2. Reconcile against the biller, not against ourselves.** The only check that would catch
this compares the platform's settled set with the *biller's*. A daily settlement file is how
real schemes do it — and it is what `EODReconciliationJob` is named after but does not do:
it asks the biller about **bills it already knows are unresolved**, never *"what do you think
you settled that I don't?"*

**3. Detect it cheaply, today.** A bill in a terminal state whose biller record disagrees.
One bulk inquiry over recently-`Rejected` bills would have caught this in a single pass — the
bulk endpoint already exists.

⚠️ **Recorded as a residual risk with a measured example**, not fixed. The cost is bounded
(only bills that revert during a biller's late-settlement window) but it is real money, and
**no existing invariant can see it.**

---

## 7 · Follow-on scenarios

- **S10 — consumer-side loss.** A payload the listener drops: invisible to `mandatory`, and
  the case that would earn end-to-end reconciliation.
- **S11 — biller enforces the window.** Teach the simulator to refuse late settlement and
  confirm the risk disappears — proving the fix is a *contract*, not more code on our side.
- **Kill mid-saga · duplicate · flood** — still untouched.

---

## 8 · Log evidence

```
T+70 s   RELEASE 77 at 08:49:50.764   (bill -> Rejected, customer refunded + notified)
T+90 s   biller settles PAID, txn BILR-dad9e4310df04f10a8b172fc016ce8cd

biller       : S09-001 PAID
bill service : S09-001 Rejected
wallet       : HOLD 77 -> RELEASE 77, balanced, zero drift
customer     : bill.payment.rejected + wallet.money.received, both SENT

nothing logged an error. nothing detected the divergence.
```
