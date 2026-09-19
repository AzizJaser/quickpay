# QuickPay — Threat Model (Phase 9, criterion 4)

- **Status:** 🔨 DRAFT — §1 is measured inventory; §2–§6 are questions awaiting the human's answers
- **Started:** 2026-09-19
- **Owner:** Abdulaziz
- **Method:** same discipline as Phase 7 — prediction before measurement, fixes earned, every
  surprise explained. This document is the input to `ASVS-L1.md` and scenarios `A01`–`A06`.

> ⚠️ **Sections 2–6 are deliberately empty.** The reviewer asks; the human answers. A threat
> model written by the tool is a tool's opinion about someone else's system.

---

## 1. Inventory — measured 2026-09-19, not asserted

### 1.1 HTTP entry points — 26 total across 7 modules

| module | endpoints | authenticates the caller? |
|---|---|---|
| **customer-service** (BFF, :8083) | `POST /v1/customer` · `PUT /v1/customer/activate` · `POST /v1/login` · `GET /v1/me/wallets` | ✅ **the only one.** `SessionFilter`, deny-by-default, 3 public paths |
| **wallet-service** (:8080) | `GET /v1/wallets/{walletNumber}` · `GET /v1/wallets/{cif}/all` · `POST /v1/wallets` · `PUT .../activate/{n}` · `PUT .../suspend/{n}` · `DELETE .../close/{n}` · `POST /v1/transfer/betweenWallets` · `/top-up` · `/withdraw` · `/hold` · `/settle` · `/revers` | ❌ **none** — except the webhook below |
| wallet-service webhook | `POST /v1/gateway/webhook` | ⚠️ **HMAC-SHA256** over the raw body, constant-time compare — the only caller verification in the money core |
| **bill-service** (:8081) | `POST /bill/create` · `POST /bill/reserve` | ❌ none |
| **notification-service** (:8082) | *(no controllers)* | n/a |
| gateway-simulator (:9090) | `/payment` · `/forge` · `/redeliver/{txnId}` | ❌ scaffolding |
| biller-simulator (:9091) | `/payments` · `/payments/inquiries` · `/payments/{ref}` · `/mode` · `/state` · `/reset` | ❌ scaffolding |
| provider-simulator (:9093) | `/email` · `/sms` · `/mode` · `/state` · `/reset` | ❌ scaffolding |

**Measured fact:** of 26 endpoints, **1 service authenticates a human** and **1 endpoint
verifies a machine caller**. Ten endpoints that move or expose money have no notion of a
caller at all. This is not a new discovery — ADR-0006 records it as the reason auth was
promoted from "purely additive" to a money-path concern.

### 1.2 Network surface published to the host (`docker-compose.yml`)

| port | service | credential |
|---|---|---|
| 5432 | wallet-db | in `application.yml`, committed |
| 5433 | bill-db | `bill` / `bill`, committed |
| 5434 | notification-db | `notification` / `notification`, committed |
| 5435 | customer-db | committed |
| 5672 | RabbitMQ AMQP | `${RABBITMQ_USER:quickpay}` / `${RABBITMQ_PASS:quickpay}` |
| 15672 | **RabbitMQ management UI** | same |
| 8080 | wallet-service *(load overlay)* | — |

⚠️ ADR-0006's security argument rests on *"the services are unreachable except through the
BFF"* and records that this is **currently false**. The table above is the evidence.

### 1.3 SQL surface (pre-inventory for A03)

Three native queries exist in the entire codebase. None interpolates a value into the string:

| file | query | input |
|---|---|---|
| `CustomerRepository:19` | `select nextval('seq_customers_cif')` | **none** |
| `WalletRepository:27` | `findDriftedWallets` | **none** |
| `WalletRepository:36` | `findWalletsByCif` | `:cif` **named parameter** |

Everything else is JPQL or a derived query. **A03's surface is three queries, not a sweep** —
but per the Phase 9 rules, "JPA parameterizes" stays a hypothesis until proven per endpoint.

### 1.4 Already closed — do not spend scenario time here

- **Actuator is not a dependency in any module.** No management endpoints exist to expose.
  A06's actuator item is answered before it is asked.
- **Session tokens are stored as SHA-256 hashes**, never plaintext. A `sessions` table dump
  yields nothing usable; no salt is needed because the input is 256 bits of `SecureRandom`,
  not a human-chosen password.
- **Passwords are BCrypt** (`BCryptPasswordEncoder` with default strength — **10**;
  confirm this is deliberate in A05).
- **Nothing logs arbitrary headers** — `CorrelationIdFilter` reads one header by name.
  ADR-0006 depends on this; A06 must re-verify it rather than inherit it.

---

## 2. Assets — *what is worth stealing, and what is the worst outcome?*

> **Q1.** Rank these four by worst case, and say which one, if compromised, ends the project:
> money movements · sessions · customer PII · idempotency keys.
>
> **Q2.** For money: is the worst outcome *unauthorized movement*, or *irreversible*
> unauthorized movement? The answer decides whether `revers` counts as a mitigation or
> merely as cleanup.
>
> **Q3.** PII held: national ID, phone, email, name. What obligation do you claim here —
> and is this project explicitly *not* claiming regulatory compliance? Say so if so; an
> unstated non-claim reads as an oversight.
>
> **Q4.** You listed idempotency keys as an asset. ⚠️ **Justify it.** What does an attacker
> *do* with a known key? If there is no answer, it comes off the list. If there is one,
> it belongs in a scenario, because it is not a confidentiality problem.

**Answers:**

_(to be written by the human)_

---

## 3. Actors — *who, holding what?*

> **Q5.** "Attacker holding a stolen session" — **how did they get it?** The vector decides
> what else they hold. A token from a log file comes with log access; a token from XSS comes
> with the ability to keep stealing; a token from a shared device comes with nothing else.
>
> **Q6.** "Malicious client developer" — do they hold a **valid session of their own**? Do
> they have the API contract? This is the actor that finds IDOR, so the capability matters.
>
> **Q7.** "Insider with DB read" — **which** database, and read-only or read-write? One
> answer makes it a confidentiality problem; the other makes it a golden-rule problem.
>
> **Q8.** Which actor is **out of scope**, explicitly? ADR-0006 already assumes an attacker
> with network position between services is out of scope. Confirm or revoke that assumption
> here, because every per-service secret decision depends on it.

**Answers:**

_(to be written by the human)_

---

## 4. Entry points — *which of the 26 are meant to be reachable, by whom?*

> **Q9.** My reading of §1.1 is that **only customer-service is intended to be
> internet-facing**, and everything else is internal. Confirm or correct.
>
> **Q10.** The four database ports and the RabbitMQ management UI (§1.2) are published to the
> host today. Is that **dev-only convenience that production removes** — or is it in scope as
> a finding? ⚠️ Answer this before A06, or A06 will "discover" a thing you already knew.
>
> **Q11.** The three simulators expose `/mode` and `/reset` endpoints that change system
> behaviour on demand. In scope, or excluded as test scaffolding that never ships?

**Answers:**

_(to be written by the human)_

---

## 5. Trust boundaries — *where does trust change, and what enforces it?*

> **Q12.** ADR-0006 puts the boundary at the BFF: the wallet trusts the caller's cif claim
> because the BFF is the only door. Is that boundary **the** control, or do you want
> defence-in-depth behind it? This is the difference between increment 5 being *sufficient*
> and being *one layer*.
>
> **Q13.** The gateway webhook is HMAC-verified. Is the gateway **trusted**, or
> **verified-then-trusted**? If a valid HMAC arrives with an implausible payload, what
> should happen?
>
> **Q14.** Between customer-service and wallet-service there is currently **no caller
> verification at all** — increment 5 adds a per-service secret. Until it lands, what is
> the boundary made of? Name it honestly; "nothing" is a valid answer and belongs on record.

**Answers:**

_(to be written by the human)_

---

## 6. Accepted risk — *declared before we start, not discovered afterwards*

> **Q15.** What are you willing to **accept**, stated now? Naming these up front is what
> stops the security pass from manufacturing findings it already knew about. Candidates
> visible from §1:
>
> - no HTTPS anywhere (dev and test)
> - no rate limiting on any endpoint, including `/v1/login`
> - dev credentials committed to git
> - no audit trail of session *use* (no `last_used_at`, IP or user-agent)
> - four databases reachable from the host
>
> For each: **accept / fix in this pass / defer with a trigger.**

**Answers:**

_(to be written by the human)_

---

## 7. Reviewer challenges to the Phase 9 plan itself

Raised 2026-09-19, before any scenario runs. Each needs a ruling.

**C1 · A01 cannot measure what it claims.** A01 asks for *"time-to-dead after `BLOCKED`"*.
**There is no way to block a customer** — the block/close endpoints are not built, and
ADR-0006's revocation (delete sessions in the same transaction as the status change) has
nothing to hang on. A01 therefore depends on **block/close**, not on increment 5. Either
build it first or A01 measures a hypothetical and reports it as one.

**C2 · A02 has a one-line answer today.** The token is returned in a **JSON response body**
(`LoginResponse`). There is no cookie anywhere in the system and no browser client at all.
"httpOnly / Secure / SameSite" cannot be verified on the wire because nothing sets a cookie.
A02 is therefore a **design decision for a client that does not exist**, not a measurement.
Reframe it as a decision record, or defer it until there is a client.

**C3 · A04 is understating what it will find.** IDOR presumes authentication exists to be
bypassed. On wallet-service and bill-service there is **no authentication at all** — ten
money endpoints, published on 8080. That is not IDOR; it is a more severe and already-known
finding. Split A04: the `/me` half can run **now** (the structural defence is already proven);
the wallet/bill half is meaningless until increment 5 gives it something to bypass.

**C4 · A03's surface is three queries** (§1.3), all parameterless or named-parameter. The
sweep is an afternoon, not a scenario — unless the goal is to prove the *hypothesis* per
endpoint, which is a different and defensible exercise. Decide which.

**C5 · A06 is already half-answered.** Actuator is not a dependency (§1.4); the git-history
credential grep can run today. What remains genuinely open is **logs and error bodies**,
which is worth doing properly.

**C6 · Schedule.** Hard stop 24 Oct; today is 19 Sep. Outstanding before this pass even
starts: increment 5 (blocked on the orphaned-wallet decision), increment 6, logout,
block/close, and the BFF's missing MockMvc tests. ⚠️ **The stated rule — increment 5 goes
first and unfinished security work becomes a recorded deferral — is the right one. Expect
to use it.**

---

## 8. Output contract

```
SEC-PASS | <date> | ASVS-L1: x/y | scenarios: 6 | predicted: n/m | fixes earned: n | deferred: n | accepted: n | top finding: ...
```