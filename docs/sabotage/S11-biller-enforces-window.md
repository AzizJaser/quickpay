# S11a — the biller enforces the settlement window

> Prediction must be written and saved **before** the run.

| | |
|---|---|
| **Date** | 2026-09-08 |
| **Services running** | wallet 8080 · bill 8081 · notification 8082 · biller-sim 9091 · provider-sim 9093 |
| **Correlation id prefix** | `S11-` |
| **Related** | direct follow-on to [S09](S09-late-settlement.md) |

---

## 1 · What I am changing, and why

S09 found the settlement window was never an agreement — `biller-settlement-window-ms` lived
only in the bill service's config, so the biller settled `PAID` 30 s after the platform had
reverted and refunded.

**S11a gives the biller the same number.** Past it, the biller refuses and stores **nothing**,
so a later inquiry returns `NOT_FOUND` and `resolve` already reverts a `NOT_FOUND` bill past
its own window. **Scaffolding only; no application code changed.**

```java
long elapsedMs = (System.nanoTime() - receivedAtNanos) / 1_000_000L;
if (elapsedMs > settlementWindowMs) {
    logger.warn("REFUSING to settle reference {} ... storing nothing");
    return new PaymentResult(..., "EXPIRED", null, "...refused");
}
settledByReference.put(req.reference(), result);   // only inside the window
```

### Two defects left in deliberately

**1. The contract has a number but no anchor.** Raised by the learner before the run:
*"but this window is a contract between the biller and bill."* The number is now agreed; the
**event it is measured from** and **whose clock measures it** are not. The bill service
measures from `bill.created_at`, the biller from when the request arrived — strictly later.

**2. `NOT_FOUND` is a lie.** The biller has a record — it refused. Right outcome, wrong
reason, and the refusal is not idempotent. An explicit `EXPIRED` status is S11b.

---

## 2 · PREDICTION  ⚠️ written before the run

Setup identical to S09: biller sleeps **90 s** (`delayMs: 90000`), bill window 60 s, one bill.

### Learner's predictions, verbatim

| Q | Prediction | Confidence |
|---|---|---|
| Q1 · does the S09 divergence disappear? | *"it will be same in S09"* | **high** |
| Q2 · does the clock mismatch matter? | *"it doesn't matter, if the bill is within the window it will accept the settle"* | **high** |
| Q3 · the refusal stores nothing | *"bill will be reject and wallet refunded"* | **high** |
| Q4 · does the golden rule hold? | *"no, bill is paid with provider and the fund is in the customer's wallet"* | **high** |

⚠️ **Flagged before the run:** all four rest on one assumption — that the biller still
settles. If that is wrong, all four move together. It is one claim tested four ways, not four
independent predictions.

**Q4 is an evolution from S09**, where the same question was answered *"yes, it will hold."*
The answer is now **"no"**, on the grounds that the biller is paid while the customer keeps
the funds — the S09 lesson internalised: the wallet's two counts cannot see a
platform-vs-biller imbalance.

**This run finally tests S09's prediction 3** — *"if the biller sticks with the agreement then
yes it will hold"* — which S09 could not test, because the biller never stuck to it.

---

## 3 · What actually happened

**Setup identical to S09** — biller decides at T+90 s, bill service window 60 s, 77 SAR.

```
  time     bill        wallet   biller_settled
  T+20s   Reserved    3558     0
  T+45s   Reserved    3558     0
  T+70s   Rejected    3635     0     <- reverted at 60 s, customer refunded
  T+100s  Rejected    3635     0     <- biller decided at 90 s and REFUSED
```

**Side by side with S09 — one digit differs, and it is the whole scenario:**

```
S09   T+70  Rejected  refunded      T+100  biller_settled = 1    DIVERGENCE
S11   T+70  Rejected  refunded      T+100  biller_settled = 0    AGREEMENT
```

Final state:

| | |
|---|---|
| bill service | `S11-002 -> Rejected` |
| biller | **0 settled references — no record of S11-002 at all** |
| ledger | `HOLD 77` 10:33:13 → `RELEASE 77` 10:34:22 |
| customer | refunded, and told: `bill.payment.rejected` SENT/SENT |

⚠️ **First attempt discarded (documented, not hidden).** It used `delayMs: 90000` to make the
biller decide late — but `delayMs` slows **`inquire` as well as `pay`**, deliberately, since
S02b. Every sweep inquiry blew the 2 s read timeout, `resolve` was never reached, and the
bill stranded at `Reserved` — **reproducing the S01/S04 mechanism instead of testing the
settlement window.** The biller still refused (0 settled with the outcome forced to
`SUCCESS`), so the substantive result held, but the timeline was worthless and timing is this
scenario's entire subject. Fixed by making `timeoutSleepMs` runtime-settable so `pay` can be
slow while `inquire` stays fast.

---

## 4 · Prediction vs reality

| Q | Predicted | Actual | |
|---|---|---|---|
| Q1 | *"it will be same in S09"* | **half.** The **bill service** behaved identically — Reserved, reverted at 60 s, refunded. The **biller** did not: 0 settled vs S09's 1. Reading (a) confirmed, reading (b) falsified | ~ |
| Q2 | *"it doesn't matter, if the bill is within the window it will accept the settle"* | **UNTESTED** — at 90 s both windows are long past, so this run cannot speak to the clock gap either way. Needs a knife-edge run | — |
| Q3 | *"bill will be reject and wallet refunded"* | **✓ exactly** — `Rejected`, 3558 → 3635 | ✓ |
| Q4 | *"no [it does not hold], bill is paid with provider and the fund is in the customer's wallet"* | **✗ FALSIFIED** — the biller was **not** paid. Nothing to reconcile | ✗ |

### 🟢 THE RESULT — S09's divergence is gone, and "the golden rule held" finally means something

S09's discomfort was that the invariants passed while money was lost: the held-money identity
and the suspense balance are **both derived from the wallet's own ledger**, two counts of the
same book, structurally unable to see a debt to the biller. "The golden rule held" was true
and hollow.

This run checks **both** measures:

| check | before | after | |
|---|---|---|---|
| held in suspense (identity) | 12 | 12 | ✓ |
| suspense balance | 12 | 12 | ✓ |
| wallet `015100000001` | 3635 | 3635 | ✓ |
| drifted wallets | 0 | 0 | ✓ |
| **platform paid to biller** | — | **0** | |
| **biller says received** | — | **0** | |
| **external difference** | — | **0 SAR** | ✅ |

**S09's prediction 3 — *"if the biller sticks with the agreement then yes it will hold"* — is
CONFIRMED.** It took two scenarios to test: S09 could not, because the biller had never heard
of the agreement.

### Why Q4 was wrong, and why being wrong here is the good outcome

Q4 assumed the biller still settles — the same assumption underneath all four answers, which
is why they moved together. It was **the right answer to S09's world**, and it shows the S09
lesson was internalised: the answer moved from *"yes, it holds"* to *"no"* on exactly the
right grounds.

The assumption failed because **the thing that changed was not in the platform at all.** No
application code was touched. `resolve`, the sweep, the wallet and the window value are
byte-identical to S09. **A one-sided timeout became a two-sided contract, and that alone
eliminated the loss** — which is the finding S09 pointed at and could not demonstrate.

---

## 5 · Did the golden rule hold?

**Yes — internally and externally, for the first time in the project.** See the table above.
Every prior scenario could only answer the internal half.

---

## 6 · What I would fix, and whether I fixed it

**BUILT (scaffolding): the biller enforces the window.** The fix S09 earned. It required no
application-code change, which is itself the point — the defect was in the *agreement*, not
the implementation.

**NOT FIXED — 1. The contract still has a number but no anchor.** Raised by the learner
before the run: *"but this window is a contract between the biller and bill."* Correct, and
only one of three terms is agreed:

| term | agreed? |
|---|---|
| the number (60,000 ms) | ✅ |
| the event it is measured from | ❌ bill service uses `bill.created_at`; biller uses request arrival |
| whose clock measures it | ❌ never discussed |

The biller's clock starts strictly later, so its window closes strictly later. **Both sides
can honour "60 seconds" perfectly and still disagree**, and in production there is clock drift
on top. This run cannot see the gap — at 90 s both windows are long past.

The real-scheme fix: **the deadline travels with the request.** The bill service computes
`expiresAt` from its own clock and sends it; the biller checks "am I past the deadline you
gave me?" One clock, one anchor, no config to keep in sync. Needs `BillerPayRequest` to carry
the field — **application code, so it is the learner's to write.**

⚠️ **Reviewer's admission on the record:** putting the number in the biller's config and
picking the anchor unilaterally **repeated the S09 mistake at smaller scale.** S11a is kept in
its naive form because it demonstrates why the number alone is insufficient.

**NOT FIXED — 2. `NOT_FOUND` is a lie.** The biller has a record: it refused. Saying "no
record" gives the right outcome for the wrong reason, and because nothing is stored the
refusal is **not idempotent** — a later `pay()` on the same reference starts a fresh window
and could settle. Harmless today only because the sweep never re-sends `pay()`, which is a
property of the current sweep rather than a guarantee. **That is S11b.**

---

## 7 · Follow-on scenarios

- **S11b — explicit `EXPIRED`.** A new term in both vocabularies. ⚠️ Ship the biller first and
  the bill service cannot deserialize it — the whole bulk inquiry throws and **every** bill in
  that pass stays `Reserved`. **A contract change has a rollout order**, the cross-service
  cousin of expand-contract.
- **S11c — the knife edge.** A settlement landing *between* the two windows: the only run that
  can test Q2, and the one that earns `expiresAt` in the request.
- **Duplicate · kill mid-saga · flood** — still untouched.

---

## 8 · Log evidence

```
10:33:13.719  HOLD 77          bill S11-002 -> Reserved
10:34:22.529  RELEASE 77       window passed, NOT_FOUND -> reverse -> Rejected
              wallet 3558 -> 3635, customer notified (bill.payment.rejected SENT/SENT)
~10:34:43     biller wakes after 90 s, sees 90,000 ms > 60,000 ms, REFUSES, stores nothing

biller settled references: 0
bill service:              S11-002 Rejected
external difference:       0 SAR
```
