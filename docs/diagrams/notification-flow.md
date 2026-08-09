# Notification flow (outbox → broker → consumer → provider)

Built 2026-08-03 → 08-09, verified end to end. Notifications is **service #3** (ADR-0005:
RabbitMQ for domain-event fan-out). The wallet publishes *facts*; notifications decides
what deserves an SMS. Class and method names below are the real ones in the code.

```mermaid
sequenceDiagram
    autonumber
    actor app as Customer app
    participant WS as wallet · WalletService
    participant WOB as wallet DB<br/>outbox_notification
    participant JOB as wallet · NotificationPublisherJob
    participant MQ as RabbitMQ<br/>quickpay.events (topic)
    participant Q as queue<br/>notification.money-events
    participant NL as notification · NotificationListener
    participant NDB as notification DB<br/>customers · processed_events
    participant PC as notification · NotificationProviderClient
    participant SIM as provider-simulator · ProviderController

    rect rgb(238,246,255)
    Note over WS,WOB: ONE transaction — money and the event commit together
    app->>WS: transfer(debited, credited, amount, idempotencyKey)
    WS->>WS: notificationEventHelper("wallet.money.sent", cif, …)
    WS->>WOB: notificationEventRepository.save(…) per CUSTOMER leg<br/>(skips internal accounts) · sent_at = NULL
    Note right of WS: rollback ⇒ no ledger row AND no outbox row
    end

    rect rgb(240,248,240)
    Note over JOB,MQ: relay — @Scheduled, at-least-once
    JOB->>WOB: findTop100BySentAtIsNullOrderByCreatedAtAsc()
    JOB->>MQ: rabbitTemplate.convertAndSend(exchange, eventType,<br/>payload, messageId = event_id)
    JOB->>WOB: setSentAt(now) + save
    Note right of JOB: publish FIRST, mark second<br/>crash ⇒ duplicate, never a loss
    end

    MQ->>Q: routes on wallet.money.*<br/>(binding declared by Topology)
    Q->>NL: NotificationListening(payload, messageId, routingKey)

    rect rgb(255,248,235)
    Note over NL,NDB: dedup — messageId is the PK
    NL->>NL: parsingNotificationMessage(payload)
    NL->>NDB: processedEventRepository.findByMessageId(messageId)
    alt already delivered on both channels
        NL-->>Q: return — redelivery, do nothing
    else new event
        NL->>NDB: customerRepository.findCustomerByCif(cif)
        alt no customer
            NL-->>Q: log.warn + return · no row written
        else customer found
            NL->>NDB: save(new ProcessedEvent(messageId, payload, attempts 0))
        end
    end
    end

    rect rgb(250,240,250)
    Note over NL,SIM: deliver(event, customer, routingKey)
    NL->>PC: smsProvider(phone, message, messageId)
    PC->>SIM: POST /provider/v1/sms
    SIM-->>PC: 200 SENT · 422 FAILED · 503 unknown
    NL->>PC: emailProvider(email, message, messageId)
    PC->>SIM: POST /provider/v1/email
    SIM-->>PC: 200 SENT · 422 FAILED
    NL->>NDB: status = (response == SENT)<br/>sent_at only if SENT · attempts++ · save
    end

    Note over NL: never throws — an escape means requeue,<br/>and a bad payload fails identically forever

    rect rgb(255,235,235)
    Note over NDB: ⚠️ NOT BUILT: retry job<br/>rows with sms_status = false sit unretried<br/>(partial indexes + maximum-retries are ready for it)
    end
```

## Why each piece is there

**The outbox write is inside the money transaction.** Committing the ledger row and then
publishing would lose the event if the process died in between — the failure handler never
runs, so nothing records that a notification was owed. Writing the row in the *same*
transaction makes the guarantee structural: **if the money moved, the obligation exists.**
Same write-ahead shape as the bill record.

**The relay publishes first and marks `sent_at` second.** Reversing them trades an
occasional duplicate for an occasional permanent loss. Duplicates are recoverable;
missing events are not.

**One identifier carries idempotency through four hops:**

```
outbox_notification.event_id  →  AMQP message_id  →  processed_events.message_id (PK)  →  provider reference
```

Because it is the *primary key* of `processed_events`, a duplicate is not merely detected —
it is impossible to insert twice.

**The consumer declares its own queue and binding** (`Topology`). The wallet declares only
the exchange. Adding history later means declaring another queue with another binding;
no publisher changes.

**The listener never throws.** An exception escaping a `@RabbitListener` tells RabbitMQ to
requeue — and a malformed payload or an unknown `cif` fails identically on every
redelivery, so it becomes a hot loop. Both are logged and dropped instead.

**Unknown `cif` writes no row.** Customers are seeded, so "unknown" means *never notify*.
A row would be permanent retry fodder — there is no "undeliverable" state, only
`false` = "retry me".

**`sent_at` is stamped only on success.** `last_attempt_at` records that we *tried*;
`sms_sent_at` records that it *went out*. A null `sent_at` therefore means "never
delivered" unambiguously — which is what the retry job will act on. (This was a real bug,
found by reading the data: statuses said `false` while timestamps claimed a delivery.)

## Verified

- Full chain, first run: 3 outbox rows → queue drained (`consumers: 1`) → 3 rows in
  `processed_events` → provider recorded the sends.
- Forced `FAILED`: statuses false, both `sent_at` null, `last_attempt_at` set.
- Forced `SENT`: statuses true, both `sent_at` set.
- Atomicity (wallet side): a transfer failing on insufficient balance writes **neither** a
  ledger row nor an outbox row.

## Open

- **The retry job does not exist.** Rows with `sms_status = false` are never retried. The
  partial indexes (`(created_at) WHERE sms_status = false`, likewise for email),
  `attempts`, `last_attempt_at` and `maximum-retries` are all in place for it. It will be
  the **third caller** of `deliver(...)`, after the new-event and redelivery paths.
- **The bill service publishes nothing yet** — no `BillPaid` / `BillRejected`, so
  notifications only sees wallet events.
- **A bill reserve also emits a wallet event**, so a bill payment will produce a wallet
  notification *and* a bill one. Suppression is a notifications-side policy call: the
  wallet publishes facts, and history needs all of them.