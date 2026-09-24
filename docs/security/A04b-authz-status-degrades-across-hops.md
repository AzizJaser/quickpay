# A04b — an authorization refusal degrades to "try again" across three hops

| | |
|---|---|
| **Date** | 2026-09-24 |
| **Services running** | customer-service (BFF), bill-service, wallet-service, biller-sim |
| **Found during** | increment 6 end-to-end test, the forged-wallet case |
| **Related** | `A04-idor-money-path.md` (the raw-curl version) · ADR-0006 · increment 5A |
| **Severity** | 🟡 medium — **money is safe**; the *status contract* is wrong |

---

## 1 · What happened

A customer (`cif 0000000012`) paid a bill naming a wallet they do **not** own
(`015100000002`, owner `0000000013`) — the money-path IDOR, now driven through the *real*
BFF → bill → wallet chain instead of a raw curl to the wallet.

**5A did its job.** The wallet refused, the forged wallet was untouched (balance 2222), no
hold persisted, golden rule held (`sum=0`, drift 0). The attack failed.

**But the status the customer received was `503 "bill service unavailable — try again"`** —
the single most misleading answer possible for a permission denial.

## 2 · Prediction vs reality

| | predicted | actual |
|---|---|---|
| human | 403 | — |
| reviewer | 503 | ✅ **503** |

The reviewer's 503 was right, and it *is the finding* — not a pass to celebrate.

## 3 · Where the meaning was lost — two defensible translations composing into a lie

```
wallet   5A ownership check fails
         → WalletCifOwnershipConflictException → HTTP 403  "forbidden"     ← the truth

bill     WalletClient.reserve onStatus(403) → CifMismatchException
         → GlobalExceptionHandler → HTTP 500  "internal error"            ← lie #1

BFF      BillClient onStatus(5xx) → BillUnavailableException
         → HTTP 503  "bill unavailable, try again"                        ← lie #2

phone    503 "bill service unavailable"
```

Neither translation is wrong *in isolation*:

- **bill maps 403 → 500** because when that arm was written, a wallet 403 was reasoned to be
  an internal inconsistency ("bill and wallet disagree about ownership — should never happen").
  ⚠️ **Increment 6 changed the premise.** Now a wallet 403 is a *routine, expected* outcome —
  a customer named a wallet they don't own. Treating a routine authorization result as a 500
  is the first defect.
- **BFF maps any 5xx → 503**, correct for a *genuine* bill failure — but bill's 500 here was a
  translated 403, not a failure.

The **composition** is the bug: an authorization decision made at the money core arrives at
the customer as an availability problem.

## 4 · Why it matters

- **503 is the one status that invites retry.** A well-behaved client auto-retries; a
  permanent denial becomes an infinite retry loop against a forbidden request.
- **It hides the attack.** In monitoring, an IDOR attempt is now indistinguishable from a bill
  outage — the most important security event on the money path is buried under "dependency
  unavailable."

This is the session-long theme, measured: **each layer translates the one below, and meaning
degrades across hops.** Same shape as ADR-0006's "the BFF must translate a 403 before it
reaches a phone" — except here three services each translate, and the result is worse than any
one of them intended.

## 5 · The fix — recorded, NOT built (Phase 9 close-out is done)

The 403 should **propagate as 403** end to end:

1. **bill**: map the wallet's 403 to a *forbidden* response (its own 403), not
   `CifMismatchException`/500. A wallet 403 is "the caller doesn't own this wallet," a client
   error, not an internal fault.
2. **BFF**: pass a bill 403 through as 403, rather than folding it into the 5xx → 503 arm.

⚠️ This reopens the enumeration question (`A04` §"known refinement"): a 403 to the phone
confirms the wallet exists and isn't the caller's. The generic-body discipline from
`/me/wallets` applies — 403 status, but a body that says only "not permitted."

Cost: a change across two services and two client error-maps. Deferred with the rest of the
auth polish; **the money was never at risk**, so this is a correctness-of-signal fix, not a
security hole.

## 6 · Evidence

```
forged wallet 015100000002 balance : 2222 (untouched)
bill BILL-FORGE-02 status          : Pending (never reserved)
sum(balance)                       : 0
drift                              : 0
wallet log: "wallet with number 015100000002 doesn't belong to cif 0000000012"  (403)
phone received                     : 503
```