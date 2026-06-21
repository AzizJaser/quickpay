# Bill-payment flow (reserve / capture / compensation)

Designed 2026-06-19 → 06-21 (NOT yet built). Bill payment is a **separate service** (service #2) so
multiple billers/integrations stay out of the core wallet. The wallet exposes **hold** (reserve,
like withdraw), **capture** (a transfer), and **reverse** (compensation). This diagram reflects the
**resolved** design — see ADR-0004 (account flags) and `learning-log.md` (2026-06-21).

```mermaid
sequenceDiagram
    autonumber
    actor app as Customer app
    participant billS as Bill service
    participant wallet as Wallet service
    participant billerGW as Biller gateway

    app->>billS: Pay bill Z for customer (client idempotency key)
    Note over billS: 1. Record intent FIRST — create bill record,<br/>status = PENDING, ref A (write-ahead, before any money move)

    billS->>wallet: Reserve / hold: customer wallet -> temp account (key_1)
    Note right of wallet: no balance -> error, end flow
    wallet-->>billS: Trx(1) ref
    billS->>billS: Persist Trx(1) on record A

    billS->>billerGW: Pay bill Z, ref A

    alt success (2xx)
        billerGW-->>billS: Paid
        billS->>wallet: Capture: temp -> biller account (key_2)
        wallet-->>billS: Trx(2) ref
        billS->>billS: Atomic flip PENDING -> CAPTURED (only the winner moves money)
    else unknown (timeout / 5xx)
        billS->>billerGW: Inquire status of bill Z
        billerGW-->>billS: Status
        Note over billS: definitive PAID -> capture path<br/>definitive FAILED -> reverse path<br/>still unknown -> leave PENDING (EOD retries)
    else failed (4xx)
        billerGW-->>billS: Failed
        billS->>wallet: Reverse Trx(1): temp -> customer (guarded by reverses_entry_id)
        wallet-->>billS: Reversal Trx ref
        billS->>billS: Atomic flip PENDING -> REJECTED
    end

    loop EOD sweep (still-pending bills)
        billS->>billerGW: Inquire status
        billerGW-->>billS: Status
        Note over billS: resolve via the same capture / reverse paths;<br/>pending > 1 day -> reverse (business call) + raise payment-ops ticket;<br/>reconciliation backstops it
    end

    Note over wallet,billerGW: Invariants — temp account never goes negative (balance >= 0);<br/>every money move is idempotent (key_1 / key_2 / reverses_entry_id);<br/>capture XOR reverse; terminal states (CAPTURED / REJECTED) are immutable.
```

**Why each piece is there**
- **Record intent before calling the biller** — a crash after the call but before the record would
  orphan money (biller may pay, nothing to reconcile). Write-ahead closes that gap.
- **Move money first, flip status last** — never mark a bill terminal until the money has actually
  moved; flip-first would strand money in the temp account.
- **Atomic `pending → terminal` flip** — the live path, the EOD sweep, and any late response all
  funnel through one guarded flip; only the winner moves money, so duplicates are no-ops.
- **Exactly-once = at-least-once retry + idempotent consumer** — the EOD sweep IS a retry, so the
  money moves must be idempotent (reserve/capture by key; reverse by `reverses_entry_id`).
- **Temp account `balance >= 0`** — a reverse-after-capture fails at the DB = golden-rule backstop
  behind the state machine (see ADR-0004).

**Open items (resolve in the bill-service schema ADR)**
- One **shared** suspense account vs. one **per bill** (ADR-0004 assumes shared).
- The bill↔biller idempotency strategy depends on the **biller's contract** (does it dedupe on a
  client-supplied reference?) — can't be finalized until that contract is known.
- A **biller simulator** (mock) still needs to be scaffolded before the flow can be built/tested.