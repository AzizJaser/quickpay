# ADR-0006 — The BFF authenticates the customer; the wallet verifies wallet ownership

- **Status:** Accepted
- **Date:** 2026-09-12
- **Deciders:** Abdulaziz
- **Relates to:** Requirement #1 (thin auth). Depends on decisions-log entry 6 (customer
  service is service #4). Extends the HMAC precedent in `GatewayWebhookController`.

## Context

Requirement #1 asks for thin auth. Nothing existed: **no `spring-security` dependency in any
module, no credential or user table in any database, and no notion of a caller anywhere on the
money path.** `LedgerEntryController` takes `debitedWalletNumber` **from the request body**, so
anyone able to reach the API could move money out of any wallet by naming it.

The plan had deferred this as *"purely additive — a filter in front of the controllers that
touches no schema, no ledger, no saga logic."* **That justification was withdrawn on 10 Sep.**
It is true of *authentication*; it is false of *authorization*. The missing rule — *"does this
caller own the wallet being debited?"* — is a **relationship between a caller and a row**. No
filter can express it, and it belongs on the money path next to `lockOrThrow`, inside the same
transaction. Deferring it was accruing cost, not avoiding it.

Service #4 is a **customer service** acting as a **BFF** for the channel (decisions-log entry
6). That makes the call chain:

```
phone/web ──session──> customer-service ──> bill-service ──> wallet
                       (the BFF)                             (the money core)
```

**Consequence: no customer credential ever reaches the wallet.** The wallet only ever sees one
service asserting *"I am acting for cif X."* It can verify the *service*. It cannot verify the
*human*. That is the confused-deputy problem, and it is the central question this ADR answers.

## Options considered

1. **Wallet trusts the BFF's cif claim outright.** Simplest. Rejected as the sole control: a
   single mistyped or swapped parameter anywhere upstream moves the wrong person's money, and
   nothing downstream would notice.
2. **Forward the customer session; the wallet validates it with customer-service.** The wallet
   would authenticate the human. **Rejected: it puts a synchronous network call on the money
   path** and makes customer-service a hard dependency of every transfer — the exact coupling
   rejected for the notification service in sabotage scenario S10.
3. **Token exchange (OAuth 2.0, RFC 8693).** The BFF swaps the session for a short-lived
   assertion signed by customer-service; the wallet verifies it offline with a public key and
   authenticates the customer transitively. **The rigorous answer, and the known upgrade
   path** — deferred because it requires asymmetric keys and key distribution, which is
   disproportionate for four services behind one front door.
4. **CHOSEN — the BFF authenticates the human; the wallet authenticates the caller and
   verifies ownership.** Two checks, two places, neither trusting the other completely.

## Decision

**Customer authentication is delegated entirely to the customer service.** The wallet
authenticates nobody.

**The wallet performs two checks:**

1. **Caller identity and scope** — a **per-service static secret**, plus a scope naming what
   that caller may do.
2. **Ownership** — the request carries `(walletNumber, cif)`, and the wallet verifies the cif
   owns that wallet. **This costs zero extra queries**: `lockOrThrow` already loads the row to
   take the lock, so the cif arrives with it.

### Scope map

| caller | scopes | endpoints |
|---|---|---|
| customer-service (BFF) | `wallet:read` `wallet:transfer` `wallet:topup` `wallet:withdraw` | `GET /{n}`, `betweenWallets`, `top-up`, `withdraw` |
| bill-service | `wallet:hold` `wallet:settle` `wallet:reverse` | `hold`, `settle`, `revers` |
| notification-service | **none** | calls the wallet nowhere — its configured `wallet-base-url` is dead config |
| gateway | *(separate — HMAC, unchanged)* | `POST /v1/gateway/webhook` |

**bill-service loses `transfer`.** It has a `WalletClient.transfer()` method today that is
**defined but never called** — dead code that would silently grant it that power the moment
someone wired it up.

### Where ownership is checked

`(walletNumber, cif)` travels **in the request body**. Ownership is verified on every debit of
a customer wallet:

| endpoint | ownership | why |
|---|---|---|
| `betweenWallets`, `top-up`, `withdraw` | ✅ checked | the BFF knows the cif |
| `hold` | ✅ checked | **the cif is threaded down the chain** — BFF → `PaymentRequest` → `HoldRequest`. Before this ADR, bill-service learned the cif only *from* the hold response, so it could not assert one. |
| `settle`, `revers` | ⚠️ scope-only | they take an `entryId` and name no wallet. **Authority is inherited from the hold**, which was ownership-checked when created, and `uq_entry_discharged_once` guarantees an entry is discharged exactly once. |

A failed ownership check returns **403**. The 403 is returned to the *BFF*, which is inside the
trust boundary — so confirming the wallet exists leaks nothing. ⚠️ **The BFF must translate it**
before anything reaches a customer's phone.

### Also decided

- **Sessions live in `customerDB` (Postgres), not Redis.** Redis is not earned. The ownership
  check needs no cache — the cif arrives with the locked row. *"Sessions belong in Redis"* is
  the same shape as *"we need a circuit breaker"* and *"we need a warehouse"*: a standard
  answer adopted before anything measured the need. Both were refused; this follows.
- **Background jobs use bill-service's own credential.** The EOD sweep reverses customer money
  on a `@Scheduled` thread with no caller; `revers` is scope-only, so this is consistent.
- **bill-service is not an entry point.** It is called only by the BFF and needs the same
  credential-and-scope treatment the wallet gets. Locking the front door and leaving the side
  door open is not a security model.
- **Static secret over HMAC signature**, chosen for simplicity. Verified safe today: nothing
  logs arbitrary headers — `CorrelationIdFilter` reads one header by name — so the secret does
  not reach the 60-day log archive. **One careless `log.info(headers)` would change that.**

## Consequences

**What this buys.** A caller cannot name a wallet without naming its owner. A leaked
bill-service secret cannot reach wallets it was not sent. A parameter mix-up anywhere upstream
is rejected by the wallet rather than executed.

**⚠️ What it does NOT buy — the assumption this design rests on.**

> The wallet cannot detect a BFF session mix-up. It verifies that the asserted cif owns the
> named wallet — so a *mismatched* pair is rejected, but a *correctly matched pair belonging to
> the wrong person* is indistinguishable from a legitimate request. **Customer authentication
> is delegated entirely to the customer service, and a session bug there is not detectable
> downstream.**

Option 3 (token exchange) is the fix, with a clear trigger: **a second channel calling the
wallet directly, or the BFF ceasing to be the only front door.**

**⚠️ Second assumption — network isolation.** Per-service secrets prove origin, not authority,
and a static secret travels on every request. The model assumes the services are unreachable
except through the BFF. **That is currently false** — `docker-compose.load.yml` publishes
`8080:8080`, so the wallet is reachable from the host. Removing the `ports:` block makes it
reachable only on the Docker network, which costs one line and makes the assumption real.

**Still open, deliberately small:**

- **A customer transfer must not name an internal wallet.** `wallet:transfer` permits any two
  wallet numbers, including `000000000003` (Suspense) and `000000000001` (TopUp). No scope can
  express this; `is_internal` is already on the row being loaded.
- **Where the ownership check sits relative to the row lock.** After the lock is free (the cif
  arrives with it) but means locking a row for a caller with no right to it; before means an
  extra read. **Phase 8 measured `Lock:tuple` contention as real**, so this is not theoretical.

**Cost to pay.** `TransferRequest`, `TopUpRequest`, `HoldRequest` and bill's `PaymentRequest`
each gain a field. Every test that calls those endpoints with a bare wallet number must supply
an owner — which is the friction the plan originally cited as a *reason to defer*. It is
better read as the exercise: **15 integration tests currently authenticate as nobody.**