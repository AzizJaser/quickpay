# A04 — IDOR on the money path (no-credential transfer between two customers)

| | |
|---|---|
| **Date** | 2026-09-23 |
| **Services running** | wallet-service (8080), notification-service (8082), wallet-db, notification-db, RabbitMQ |
| **Correlation id / prefix** | `A04-IDOR-money-path` · idempotency `A04-IDOR-20260923-001` |
| **Related roadmap topic** | Phase 9 criterion 4 · ADR-0006 (authorization on the money path) · DoD Gate 1 |
| **Result** | ✅ **4 of 4 predicted.** The hole is confirmed and measured. |

---

## 1 · What I am breaking, and how

Nothing is broken — that is the finding. `POST /v1/transfer/betweenWallets` on wallet-service
is called **with no credential of any kind** (no `Authorization`, no session, no service
secret), naming one customer's wallet as the source and a *different* customer's wallet as the
destination. This is the confused-deputy / IDOR case ADR-0006 opens with:

> `LedgerEntryController` takes `debitedWalletNumber` from the request body, so anyone able to
> reach the API could move money out of any wallet by naming it.

`transfer()` validates amount, distinct wallets, idempotency, both-Active, and sufficient
balance. **It never asks who is calling or whether they own the debited wallet.** The only
thing protecting customers today is that the BFF does not expose this endpoint — and ADR-0006
records that the network-isolation assumption behind that is *currently false* (8080 is
published to the host).

**Request:**
```
POST http://localhost:8080/v1/transfer/betweenWallets     (no auth header)
{ "debitedWalletNumber":"005100000001",   # cif 0000000012, balance 16754
  "creditedWalletNumber":"015100000002",  # cif 0000000013  ← different customer
  "amount":500 }
```

---

## 2 · PREDICTION  ⚠️ written and locked BEFORE the run

Human's four predictions, verbatim: **1 — 200. 2 — yes (money moves). 3 — golden rule stands.
4 — two notifications, one credit and one debit, to the two wallets.**

| where | expectation |
|---|---|
| HTTP status | 200 |
| `wallet` balances | debited −500, credited +500 |
| `sum(balance)` | 0 (unchanged) |
| drift | 0 |
| `outbox_notification` | 2 rows — `wallet.money.sent` + `wallet.money.received` |

---

## 3 · WHAT ACTUALLY HAPPENED

| # | predicted | measured | |
|---|---|---|---|
| 1 | 200 | **200**, `entryId ec9741bb-fc2c-4ab5-a2b9-aee7947faaae` | ✅ |
| 2 | money moves | `005100000001` **16754 → 16254**, `015100000002` **1222 → 1722** | ✅ |
| 3 | golden rule holds | `sum(balance)=0`, **drift=0** | ✅ |
| 4 | 2 events, sent + received | `wallet.money.sent` cif `0000000012`; `wallet.money.received` cif `0000000013`; **both published** | ✅ |

Ledger row:
```
ec9741bb…  005100000001 -> 015100000002   -500 / +500   TRANSFER
```
Both notification events carried the correlation id `A04-IDOR-money-path` end to end and were
delivered.

---

## 4 · WHAT THIS MEANS — the finding

**From every internal signal, this was a perfect transaction.** The golden rule held. The
ledger balances. Both parties were notified *accurately* — the victim received a truthful
"you received 500". **Nothing anywhere records that no one authorised it.**

That is the entire point. QuickPay's invariants prove money was **conserved**, not that its
movement was **permitted**. Conservation and authorization are different properties, and the
system enforces only the first. An attacker who can reach port 8080 — which the host can
today — moves money out of any named wallet, and every health check, every reconciliation
query, every notification says the system is behaving correctly.

This is Phase 7's headline (*the golden rule holds, and that was never as reassuring as it
looked*) reproduced on the money path with an adversary rather than a fault.

**Severity: critical.** Direct, unauthenticated theft from any wallet, invisible to every
existing control.

---

## 5 · THE FIX — increment 5A (this session)

Nullable `OwnershipAssertion(walletNumber, cif)` passed into `transfer()` and checked
immediately after `lockOrThrow` (zero extra queries — the cif is already on the locked row).
`betweenWallets` / `topUp` / `withdraw` / `hold` supply it (naming the customer-side wallet,
which differs per operation); `settle` / `revers` pass `null` (scope-only, per ADR-0006).

⚠️ **5A closes the *mismatch* case: a caller can no longer name a wallet whose owner it cannot
also name.** It does **not** close the whole hole — a caller who knows a real `(wallet, cif)`
pair, or who names the debited wallet's own cif, is still unauthenticated. That is **5B
(service-to-service auth), which is DEFERRED** — see the decisions log and `A04b` below.

The "after" re-run of this exact curl is recorded in **§6**.

---

## 6 · AFTER 5A — re-run, 24 Sep (predictions 403 / 200 / holds, all correct)

5A adds a nullable `OwnershipAssertion(cif, walletNumber)` checked immediately after
`lockOrThrow`, against the already-locked row (zero extra queries). `betweenWallets` asserts
the debited wallet; `topUp` the credited; `withdraw`/`hold` the debited; `settle`/`revers`
pass `null`. `TransferRequest`/`TopUpRequest` gained a `@NotBlank cif`.

| test | request | result |
|---|---|---|
| **1 · forged pair** | `cif 0000000013` on wallet `005100000001` (really `0000000012`'s), no credential | **403** — `WalletCifOwnershipConflictException` |
| **2 · honest pair** | `cif 0000000012` on its own wallet, **still no credential** | **200, money moved** |
| **3 · golden rule** | after both | `sum(balance)=0`, drift `0` |
| **4 · original A04 body** | no `cif` field at all (the 23 Sep payload verbatim) | **400** — `@NotBlank` |

```
23 Sep (A04):  forge any wallet, no cif             -> 200, money moved
24 Sep (5A):   forged cif                            -> 403
               no cif                                -> 400
               honest cif, no service credential     -> 200   <-- still open (5B)
```

### ⚠️ What 5A does NOT close — the documented hole (decisions-log)

**Test 2 succeeded with no credential of any kind.** 5A proves the asserted cif *owns* the
named wallet; it does **not** prove the caller is *authorised to act for that cif*. Any caller
who names the true owner — a value that is not secret — moves the money. Ownership ≠
authentication. Closing this is **5B (per-service secrets + scope map), deferred by decision**
this session. Until 5B lands, the only thing keeping an attacker off port 8080 is network
position, which `docker-compose` does not currently enforce (8080 is published to the host).

### Known refinement (not fixed)

The 403 body reads *"Wallet number: 005100000001 doesn't belong to cif number: 0000000013"* —
it confirms the wallet exists and names it. Tolerable now (the caller is the BFF, inside the
trust boundary per ADR-0006), but it is the same enumeration leak removed from `/me/wallets`.
Make `super()` generic, keep the specifics in the already-present `logger.warn`. Low severity.
