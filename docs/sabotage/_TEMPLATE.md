# S00 — <short name of what you break>

> Copy this file to `S01-<slug>.md` and fill it in **as you go**, not afterwards.
> The prediction section must be written and saved **before** the run. That is the
> whole discipline — a prediction written after the fact is a description.

| | |
|---|---|
| **Date** | |
| **Services running** | |
| **Correlation id / prefix** | *(use a distinctive one — `SABOTAGE-01-…` — so the grep is clean)* |
| **Related roadmap topic** | *(e.g. #5 sagas/timeouts/circuit breakers)* |

---

## 1 · What I am breaking, and how

*One paragraph. The exact mechanism — which service, which switch, how long.*

*Example: force the biller simulator to `SERVER_ERROR` for five minutes while three bills
are mid-payment, using `POST /simulate/mode`.*

---

## 2 · PREDICTION  ⚠️ write this BEFORE running anything

**What I expect to happen, step by step:**

1.
2.
3.

**What I expect to see in the data** *(be specific — table, column, expected value)*:

| where | expectation |
|---|---|
| `bill.status` | |
| `ledger` | |
| `processed_events` | |
| broker | |

**What I expect in the logs:**

**What I expect the customer to experience:**

**Confidence:** *(high / medium / guessing — record it honestly; the guesses are the
interesting ones)*

---

## 3 · What actually happened

*Paste the real evidence — query output, log lines, broker counters. Raw, not summarised.*

```
```

---

## 4 · Prediction vs reality

| # | Predicted | Actual | ✓ / ✗ |
|---|---|---|---|
| 1 | | | |
| 2 | | | |

**Surprises — anything I did not predict:**

*Every surprise must be explained, not just noted. An unexplained surprise means the mental
model is still wrong, and that is the finding.*

---

## 5 · Did the golden rule hold?

**Money is never created or destroyed.** Check it, do not assume it:

```sql
-- held in suspense, by type
SELECT SUM(credited_amount) FILTER (WHERE transaction_type='HOLD')
     - SUM(credited_amount) FILTER (WHERE transaction_type='SETTLEMENT')
     - SUM(credited_amount) FILTER (WHERE transaction_type='RELEASE') AS held
FROM ledger;

-- and independently, the account balance
SELECT balance FROM wallet WHERE wallet_number = '000000000003';
```

| check | before | after | agree? |
|---|---|---|---|
| held (identity) | | | |
| suspense balance | | | |
| any wallet drift (`findDriftedWallets`) | | | |

---

## 6 · What I would fix, and whether I fixed it

*A scenario earns its fix. State what the failure argues for, then decide.*

**The failure argues for:**

**Decision:** *(fix now / record and defer / accept — with the reason)*

**If fixed — what changed, and the evidence it worked:**

---

## 7 · Follow-on scenarios this suggests

*Breaking one thing usually reveals two more.*

-
-

---

## 8 · Log evidence (preserved)

*Rolling logs are deleted after 7 days. Paste the lines that matter HERE — this file is
the record, the log file is not.*

```
```