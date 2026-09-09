# QuickPay

A miniature SAR digital-wallet payment platform — built end to end, then deliberately broken
for six days to find out what it actually guarantees.

Three Spring Boot services, one broker, three databases, and a double-entry ledger whose
central rule is that **money is never created or destroyed**. The interesting part is not the
build; it is [Phase 7](docs/sabotage/PHASE7_REPORT.md), where that claim was attacked 14 times
with a written prediction committed before every run.

---

## What it does

A customer pays a bill from their wallet. The money is held in a suspense account, sent to an
external biller, and either captured or refunded — and the customer is told which.

```mermaid
sequenceDiagram
    participant C as Customer
    participant B as bill-service
    participant W as wallet-service
    participant X as biller (external)
    participant N as notification-service

    C->>B: POST /bill/reserve
    B->>B: record intent (Pending) — write-ahead, no money moves
    B->>W: hold — customer wallet → suspense
    W-->>B: entryId + cif
    B->>X: pay (async, 2s read timeout)
    alt biller answers PAID
        B->>W: settle — suspense → biller account
    else FAILED, or window expires with no answer
        B->>W: reverse — suspense → customer
    end
    B->>N: outbox → RabbitMQ → bill.payment.*
    N->>C: SMS + email
```

If the biller never answers, an **EOD reconciliation sweep** inquires until it does — bounded
by a **settlement window** that both sides now honour ([S09](docs/sabotage/S09-late-settlement.md),
[S11a](docs/sabotage/S11-biller-enforces-window.md)).

---

## Architecture

| component | port | database | role |
|---|---|---|---|
| `wallet-service` | 8080 | 5432 | the money core — ledger, balances, holds |
| `bill-service` | 8081 | 5433 | the saga — reserve → pay → capture/reverse |
| `notification-service` | 8082 | 5434 | SMS/email delivery with retry |
| RabbitMQ | 5672 / 15672 | — | `quickpay.events` topic exchange |
| `gateway-simulator` | 9090 | — | mock card gateway *(scaffolding)* |
| `biller-simulator` | 9091 | — | mock biller — fault injection *(scaffolding)* |
| `provider-simulator` | 9093 | — | mock SMS/email provider *(scaffolding)* |

**One database per service.** No service reads another's tables; they talk over HTTP and AMQP.

Events reach the broker through a **transactional outbox** — the event row is written in the
same transaction as the money movement, and a relay publishes it afterwards. Phase 7 showed
precisely what that does and does not guarantee ([S08](docs/sabotage/S08-broken-binding.md)).

---

## The money model

Every movement is a **double-entry ledger row**, enforced by database constraints rather than
by application code:

```sql
CHECK (debited_amount + credited_amount = 0)   -- the golden rule, per row
CHECK (credited_amount > 0)
CHECK (debited_wallet_number <> credited_wallet_number)
UNIQUE (idempotency_key)                       -- one request == one movement
UNIQUE (coalesce(reverses_entry_id, settles_entry_id))   -- a hold is discharged once
CHECK  (reverses_entry_id IS NULL OR settles_entry_id IS NULL)
```

Customer money never vanishes into a variable — it moves between **internal accounts** that
are themselves wallets, so every balance is reconstructable from the ledger alone:

| wallet | purpose |
|---|---|
| `000000000001` | Internal TopUp — where deposits originate |
| `000000000002` | Outward Transfer — where withdrawals go |
| `000000000003` | Suspense — holds live here between reserve and settle |
| `000000000004` | Bill Account — the biller's settlement account |

Two independent checks are run after every sabotage scenario: the held-money identity
(`SUM(HOLD) − SUM(SETTLEMENT) − SUM(RELEASE)`) and the suspense account's own balance.
[S09](docs/sabotage/S09-late-settlement.md) explains why *two counts that share a source are
one count* — the most useful thing the project has learned.

---

## Running it

**Requires** JDK 21 and Docker.

```bash
docker compose up -d          # 3 × postgres + rabbitmq
mvn -B verify                 # build + Testcontainers integration tests

# then run each service (IDE or jar)
java -jar wallet-service/target/wallet-service-0.0.1-SNAPSHOT.jar
java -jar bill-service/target/bill-service-0.0.1-SNAPSHOT.jar
java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT.jar
java -jar scaffolding/biller-simulator/target/biller-simulator-0.0.1-SNAPSHOT.jar
java -jar scaffolding/provider-simulator/target/provider-simulator-0.0.1-SNAPSHOT.jar
```

Pay a bill:

```bash
curl -X POST localhost:8081/bill/reserve \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-001' \
  -d '{"billReference":"ELEC-001","walletNumber":"015100000001","amount":77}'
```

The simulators expose a control panel for deterministic fault injection:

```bash
curl -X POST localhost:9091/simulate/mode \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"TIMEOUT","delayMs":0,"timeoutSleepMs":90000}'
```

`SUCCESS · FAIL · SERVER_ERROR · TIMEOUT · NORMAL` — this is how every Phase 7 scenario was run.

---

## Phase 7 — sabotage

**[Read the full report →](docs/sabotage/PHASE7_REPORT.md)**

14 documented runs. Every prediction written and committed *before* its run; **~56% were
right**, and the misses taught more than the hits. Five runs falsified a premise the run
itself was built on.

**The golden rule held in all 14 runs — and the most valuable finding is that this was never
as reassuring as it looked:**

- **[S09](docs/sabotage/S09-late-settlement.md)** — the golden rule held *while money was
  lost*. Both counts derive from the wallet's own ledger, so neither can see a debt to the
  biller.
- **[S10](docs/sabotage/S10-consumer-side-loss.md)** — the invariants protect money; nothing
  protects the customer's knowledge. Also: `5 retries × 5 s` = **25 seconds** before a
  notification is destroyed permanently.
- **[S12](docs/sabotage/S12-duplicate.md)** — under concurrency every code-level check caught
  **0 of 10**, while the database constraints caught **9 of 9**.

**Four fixes were built.** A circuit breaker was **not** — across five scenarios. S02b earned
it; [S03](docs/sabotage/S03-bulk-inquiry.md) then withdrew the verdict by deleting 80% of the
cost with batching. *Before adding a mechanism to manage a cost, ask whether the cost can be
removed.*

---

## Documentation

| file | what it is |
|---|---|
| [`PROJECT_PLAN.md`](PROJECT_PLAN.md) | the living plan — current state, decisions, ordered backlog, `NEXT ACTION` |
| [`docs/sabotage/`](docs/sabotage/) | one record per scenario: prediction, result, what was fixed and what was not |
| [`docs/sabotage/PHASE7_REPORT.md`](docs/sabotage/PHASE7_REPORT.md) | the synthesis — start here |
| [`CLAUDE.md`](CLAUDE.md) | rules for AI agents working in this repo |

---

## Scope

Deliberately bounded, and the bounds are enforced: **max 4 services**, one database per
service, RabbitMQ for events, Docker Compose only — no Kubernetes, no Spring Cloud, no
gold-plating. The external gateway, biller and SMS/email provider are mocks under
`scaffolding/`.

A fourth service (transaction history) is parked. Everything under `scaffolding/` is test
infrastructure, not product.