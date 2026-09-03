# QuickPay — Project Plan & Session Handover

> **Read this first.** This is the living plan for the whole project. It survives lost
> sessions and context resets. Whoever (human or AI) picks the project up should read
> this file, then act. **Keep it updated** — when a milestone lands or a decision is
> made, edit this file in the same commit.
>
> Last updated: **2026-09-03 (rev 11 — THE BUILD IS DONE. bill→notification verified end to end; Phase 7 sabotage is next)**

---

## ▶ NEXT ACTION (update this line every session)

**▶ NEXT: PHASE 7 — SABOTAGE.** The build is complete. Three services, end to end, and
**one bill payment produces exactly one customer notification.** Nothing is blocking.

**Three scenarios already found by building, not by imagining:**
1. **Circuit breaker (closes topic #5).** Biller down 5 min. The EOD sweep is **serial** —
   `fixedDelay` waits for completion and each `inquire` burns the full 2s timeout, so *N*
   stranded bills cost *N* × 2s per pass and the sweep stops keeping up with its own
   interval. Predict, watch it burn, then add Resilience4j as the fix the scenario earned.
2. **The silent drop.** Measured live: `publish_in 79` vs `publish_out 72` — seven messages
   accepted by the exchange and routed nowhere, no error, and an **outbox reporting
   success**. Break a binding and watch it. (`publish_in − publish_out` is a Phase 8
   dashboard metric; the loud fix is publisher confirms + `mandatory`.)
3. **Stranded bills — two are in the database right now.** A payment whose biller call never
   landed is **invisible to the sweep**: it inquires, gets `NOT_FOUND`, does nothing,
   forever. The sweep can only resolve bills the biller knows about. Argues for a
   "reserved longer than N" alert the sweep cannot itself provide.

**Done since rev 9:** ledger transaction types (V13–V19, `NOT NULL`), `hold`/`settle`
endpoints with the discharge invariants, bill service switched onto them and reconciling
on a structured 409, hold-lifecycle notifications suppressed, bill outbox table + write.

🔄 **Two decisions taken 31 Aug — see the ◀ NEXT block for the full reasoning:**
1. **The four-services gate is DROPPED.** Sabotage runs in **September on the three-service
   system**; history #4 is parked to the Kafka project by v2.2 and gets its own addendum.
2. **Circuit breakers are sabotage-driven, not pre-built.** Topic #5 closes *inside* Phase 7,
   as the fix a "biller down for five minutes" scenario earns. No Resilience4j before then.

*(The rest of this section is the history of how #3 and traceability were built. The
decision point is the ◀ NEXT block further down.)*

✅ **Done so far** (branch `feat/notifications`, pushed):
- RabbitMQ in docker-compose — AMQP 5672, management UI http://localhost:15672
  (quickpay/quickpay). `spring-boot-starter-amqp` + `spring.rabbitmq.*` in the wallet.
- **Reliable publishing decided: transactional outbox** (was Step 0). Rejected
  "write the row only when publishing fails" — a crash between commit and publish
  skips the failure handler entirely, so the event is lost with no trace. The row
  must be written *in the same transaction as the money move*: if the money is
  committed, the obligation is committed.
- **V11 `outbox_notification`** + `NotificationEvent` entity + repository +
  `MoneyMovedPayload` record. Write lives inside `transfer`'s `@Transactional`.
  One event per **customer leg** (`!wallet.isInternal()`): top-up → 1, P2P → 2,
  suspense/biller legs → 0. Event types `money-sent` / `money-received`.
  Serialisation failure → unchecked `ParsingNotificationEventException` → rollback
  (fail closed: never move money you cannot account for).
- Partial index `(created_at) WHERE sent_at IS NULL` — holds only the unsent
  backlog, ~8 kB regardless of table size, and eliminates the sort.
- Verified: top-up → 1 row, P2P → 2 rows, both `sent_at` null; a transfer failing on
  insufficient balance writes **neither** a ledger row nor an outbox row (atomicity).

✅ **Relay job DONE** (`c6c8af6`): `NotificationPublisherJob` polls
`findTop100BySentAtIsNullOrderByCreatedAtAsc()` (matches the V11 partial index
exactly), publishes each to the **`quickpay.events`** topic exchange, then marks
`sent_at`. **Publish first, mark second** — marking first would lose the event on a
failed publish; this way it just retries. At-least-once by design. Each message
carries the outbox id as the AMQP **`message_id`** (the consumer's dedup key) plus
`contentType: application/json`. Per-event try/catch so one bad event can't abort the
batch. Routing keys are dotted — `wallet.money.sent` / `wallet.money.received` — so
consumers bind selectively (`wallet.money.*`, `wallet.#`) without the publisher
knowing they exist. Verified on the broker: right keys, right message_id, right
content type; outbox rows flip to sent.

**Broker gotcha learned:** Spring declares exchanges **lazily, on first connection**,
not at app startup. The exchange won't exist until the first message is published —
don't assume topology exists just because the service is up.

✅ **Notification service (#3) BUILT** — module, own DB on 5434, port 8082, V1 schema
(`customers` + `processed_events` with two partial indexes), entities, repositories,
queue+binding topology, provider client, and the consumer. Committed on `feat/notifications`.

✅ **Provider simulator** (`scaffolding/provider-simulator`, port 9092) — mock SMS/email
with `/simulate/mode` forcing SENT / FAILED / SERVER_ERROR / TIMEOUT and a **0.3 default
failure rate**, because requirement 5 says the channel "fails regularly" and a log-only
sender would have left the whole retry design as unreachable code. Sends are idempotent
per (channel, reference).

**Design decisions made:**
- Customers are **seeded**, so an unknown `cif` means "never notify" → log and drop,
  write no row (a row would be permanent retry fodder — there is no "undeliverable" state).
- **Contact details are looked up fresh at delivery time**, never snapshotted into the
  event — so a changed phone number applies to retries too. This is why events carry
  `cif` and not contact details.
- The listener **never throws**: an escape means requeue, and a malformed payload or
  unknown customer fails identically forever — a hot loop. Log and return instead.
- `deliver(event, customer, routingKey)` is shared by the new-event and retry paths;
  the retry job will be its third caller. Message text resolved **once** from the routing
  key (the payload carries no direction).

✅ **END-TO-END VERIFIED** — customers seeded, full chain exercised: transfer → outbox →
relay → `quickpay.events` → queue → listener → provider. Outbox rows flip to `sent_at`,
`processed_events` rows appear with per-channel status. Both failure and success paths
driven via `/simulate/mode`.

**Bug found by reading the data (not the code):** `sms_status = true` but `sms_sent_at`
null. First fix set the timestamp unconditionally, which produced the *contradictory*
state `status = false, sent_at = <time>`. Correct rule: **the timestamp is set only on
success and nulled on failure** — status and timestamp must never disagree.

✅ **`routing_key` added via the full expand-contract dance** (V2–V6). Needed because the
retry job must know the direction (`wallet.money.sent` vs `...received`) to render a
message, and the direction lives *only* in the AMQP routing key — the stored payload
does not carry it.

| step | migration | what it bought |
|---|---|---|
| 1. Expand | V2 — **nullable** column | old rows and new code coexist; no downtime |
| 2. Populate | entity + listener | proven live: 3 new rows carried real keys while 12 old stayed null |
| 3. Backfill | V3 — `UPDATE … WHERE routing_key IS NULL` → `'unknown'` | idempotent; every row satisfies the constraint *before* it exists |
| 4. Contract | V4 `NOT VALID` → V5 `VALIDATE` → V6 `SET NOT NULL` | the blocking full-table scan is decomposed away |

Sentinel is `'unknown'`, not a guessed direction — the true value is underivable from the
stored payload, and an honest sentinel prompts the right question later where a plausible
guess would be quietly believed. Verified after apply: `attnotnull = t` **and**
`convalidated = t`; a null insert is rejected by the column-level NOT NULL (which Postgres
checks *before* table constraints), leaving the CHECK as droppable scaffolding.

✅ **Terminal state added (V7/V8 + dual-write).** A `boolean` holds two values but the
system has three situations: *pending*, *sent*, and *tried N times and gave up*. With a
boolean, the last two are both `false`, so the partial index `WHERE sms_status = false`
could never shed dead rows — it would grow forever — and the unconditional `attempts++`
kept incrementing rows nobody would ever send. **Both bugs were one root cause: no way to
say "done, but not successfully."**

Fix: `sms_state` / `email_state` as `varchar(10)` + `CHECK` (**not** a native PG enum —
§9; reproduced the exact `column is of type notification_state but expression is of type
character varying` failure before backing it out). Per channel, because SMS and email
succeed and fail independently — a single row-level "exhausted" flag overwrote a
delivered channel's `SENT` with `FAILED`.

Transitions live in `deliver()`, one place per state: success → `SENT`; failure with
budget left → `PENDING`; failure with budget gone → `FAILED`. The budget test is
`getAttempts() + 1 >= MAXIMUM_RETRIES` — **the `+1` is load-bearing**: `attempts` is
incremented at the *bottom* of the method, so without it the inner test is the exact
negation of the outer guard and the `FAILED` branch is unreachable dead code.

This is a column **replacement**, not an addition, so it needs a step `routing_key` didn't:
**dual-write**. `deliver()` writes boolean *and* enum on every change, so old and new code
can coexist and a rollback still finds accurate booleans. Verified live on both paths
(`SENT`→`SENT`+timestamp, `FAILED`→`PENDING`+null), **0 mismatches** across all rows.

✅ **Indexes switched to state (V9).** Two new partial indexes on `(created_at)
WHERE <channel>_state = 'PENDING'`, old boolean-keyed ones dropped. Built with
`CREATE INDEX CONCURRENTLY` (a plain `CREATE INDEX` takes `SHARE`, which blocks every
INSERT/UPDATE for the build) — which cannot run inside a transaction, so the migration
needs a sibling script-config file `V9__….sql.conf` containing `executeInTransaction=false`.
Verified: index used, 9 pending rows, and a plain `Index Scan` returns them in
`created_at` order with **no Sort node** (a Bitmap Index Scan loses ordering and still
sorts — only a straight index scan gets the ordering free).

⚠️ **This one bit hard — two failures worth remembering:**
1. **Flyway deadlocked against itself.** `CREATE INDEX CONCURRENTLY` waits for all
   in-flight transactions to finish; Flyway holds *its own* connection open in a
   transaction to guard the history table. `pg_blocking_pids` showed the migration
   connection blocked by Flyway's lock connection — it would have waited forever.
   Fix: `spring.flyway.postgresql.transactional-lock: false` (session-level advisory
   lock instead of a transactional one).
2. **Half-applied with no record.** Statement 1 completed; statements 2–4 never ran; no
   `v9` row was written. A transactional migration would have rolled the whole thing
   back. Rule: **a migration that cannot roll back must be idempotent** — `IF NOT EXISTS`
   on every create, `IF EXISTS` on every drop, so a re-run skips what already landed.

✅ **Volume test — the index question, answered.** 500k rows with only 50 `PENDING` (the
real shape of a queue table: huge history, tiny working set).

| | |
|---|---|
| table heap | 163 MB |
| partial index | **16 kB** — 50 entries in one leaf page |
| literal query | Index Scan, **0.024 ms** |
| forced seq scan | **55 ms** (500k rows filtered, then sorted) |
| bind param, 6th/7th exec | **still Index Scan** |
| forced generic plan | **119 ms** seq scan |
| the live job | both indexes used, +6 scans each |

The custom-plan question resolved: Postgres keeps re-planning with the real value because
the custom plan is ~7,000× cheaper, so `auto` never switches to a generic plan. But the
forced case proves a generic plan genuinely *cannot* use a partial index — the protection
is the size of the cost gap, not a guarantee.

**The biggest win is the idle case, not the busy one.** `fixedDelay` fires forever
regardless of whether there is work (measured: pending=0 and `idx_scan` still climbing).
That's ~34,560 empty polls/day. At 0.017 ms each, invisible; at 119 ms each, a permanent
CPU burn on an idle system. The partial index is what makes "find nothing" cheap — and
that is the real argument for the polling interval.

**Retry maths validated:** 50 rows, 0.3 failure rate, cap 5 → attempts distribution
21/17/7/3/2 and **exactly one** channel hit the cap, against a predicted 0.24. Five
retries turn a 30% failure rate into a 0.24% chance of permanent loss.

**Unplanned finding:** the pkey scan counter rose ~+98 for ~100 saves. The job holds
**detached** entities (no `@Transactional`, `open-in-view: false`), so `save()` issues a
`merge` = **`SELECT` before `UPDATE`** — double the write cost, invisible in the code.
Left as-is deliberately: the alternative is holding a DB transaction across provider HTTP
calls, which is far worse. Worth remembering before the Phase 8 load test.

✅ **Schema finished (V10–V13).** State columns contracted to `NOT NULL` (`NOT VALID` →
`VALIDATE` → `SET NOT NULL`) — grouped two statements per file this time, because the
split rule is **by lock strength and scan cost, not statement count**: V10 is catalog-only,
V11 scans but under a weak lock, V12 skips its scan entirely. Then dual-writing stopped,
the entity fields removed, and `sms_status`/`email_status` **dropped** along with the three
now-redundant `..._not_null` CHECKs (scaffolding that existed only to let `SET NOT NULL`
skip its scan). The two value CHECKs stay — they are the substitute for a native enum.

**Final schema:** `message_id` (PK/dedup), `payload`, `attempts`, `routing_key`,
`sms_state`, `email_state` all `NOT NULL`; `sms_sent_at` / `email_sent_at` /
`last_attempt_at` nullable audit columns; two state-keyed partial indexes; nothing else.
Verified end to end after the drop — transfer → outbox → relay → queue → listener →
provider → state, with `ddl-auto: validate` passing.

**Decision:** `last_attempt_at` stays **audit-only — no backoff**. Retries fire at a fixed
interval. Noted as a deliberate choice, not an oversight: exponential backoff (query rows
whose `last_attempt_at` is older than the backoff for their attempt count) is the obvious
upgrade if a sustained provider outage ever burns all five attempts inside five minutes.

⏳ **Deferred, all non-blocking:** the listener parses the payload twice and does the
customer lookup before the `PENDING` guard (a wasted parse + query per duplicate);
`extractCustomerFromMessage` is bypassed in the new-event branch; the `FAILED` bucket
cannot distinguish "provider down" from "customer gone" from "payload corrupt" — one more
`CHECK` swap if that ever matters operationally.

✅ **Retry job DONE** (`ResendingJob`). `@Scheduled`, **two** queries — one per channel,
each matching one partial index (a single `OR` across both columns could use neither) —
merged into a `LinkedHashMap` keyed on `message_id`. The merge is not cosmetic: every one
of the 9 pending rows was pending on *both* channels, so without it `deliver()` would run
twice per round, doubling provider calls and burning the 5-attempt budget in ~2 rounds
instead of 5. `LinkedHashMap` (not `HashMap`) so the `OrderByCreatedAtAsc` fairness
survives the merge.

Per-row try/catch, never around the loop — one bad row must not abort the batch.
`CustomerNotFoundException` and `JsonProcessingException` are **permanent** (a stored
payload will never parse; a deleted customer will never return), so both mark the row
terminal rather than leaving it to spin forever. Third catch on `Exception` so an
unexpected provider/DB error costs one row, not the batch.

**Verified end to end:** 9 rows went `attempts 1 → 5` over four rounds, flipped to
`FAILED`, and the pending set emptied — the job now finds nothing. First time `FAILED`
was ever written by live code rather than a migration, and `attempts` provably stops
climbing because the row leaves the *query*, not merely changes state.

⚠️ **The indexes were NOT used** — `idx_scan` did not move (the `2` on the SMS index is
from two forced `EXPLAIN ANALYZE` runs). At 19 rows the planner correctly prefers a seq
scan. So the index switch is **unproven under load**, and two causes are currently
indistinguishable: the tiny table (certain) and the parameterised-enum/custom-plan concern
(`sms_state = ?` needs a custom plan for the planner to prove it implies the partial
index). Only a volume fixture separates them.

✅ **TRACEABILITY DONE (Bucket D)** — landed ahead of Phase 7 as agreed, because a
sabotaged flow across three services is unreadable without it.

**MDC is per-thread and per-JVM — there is no shared store.** The id propagates by being
*copied* at every boundary, and each boundary needs its own mechanism:

| boundary | mechanism | why |
|---|---|---|
| inbound HTTP | `CorrelationIdFilter` — **accept if present, generate if absent** | generating unconditionally mints a fresh id per hop and breaks the chain |
| outbound HTTP | `RestClient` request interceptor (mirror of the filter) | reads MDC → header |
| `@Async` | `CustomTaskDecorator` | `decorate()` runs on the **caller** (capture), the returned `Runnable` on the **worker** (restore) |
| `@Scheduled` | generate a **run id** per firing (`relay-`, `eod-`, …) | nothing to inherit; groups one sweep's work |
| **outbox → relay** | **a database column** (V12 / V14) | the writer thread is dead and its MDC wiped — nothing to copy |
| AMQP | built-in `correlation_id` property | symmetry with `message_id`, visible in the management UI |

**Two ids, two jobs.** A batch job's own MDC describes *the run*; each item carries *its
own* stored id. Publishing the run id onto messages would merge a hundred unrelated
customers into one apparent trace — worse than no id, because it looks correct.

**Fail open for diagnostics.** Both correlation columns are permanently nullable: the
outbox write is inside the money-move transaction, so `NOT NULL` would roll back a
transfer over a missing debugging field. The same rule forced a **length cap in the
filters** — an oversized inbound header would overflow `varchar(70)` and roll back the
transfer, defeating the rule through the back door. Generate rather than truncate: a
truncated id looks real and matches nothing upstream.

**A correlation id is a key with nothing to unlock unless something logs.** The plumbing
was complete and *invisible* — a grep returned one unrelated warning, because 15 of the
wallet's 16 logger calls were in `GlobalExceptionHandler`. Three `INFO` lines at the
boundaries (ledger written / event published / message received) turned it into a real
trace: **one transfer → five lines across two JVMs, four threads and a broker, from a
single grep.**

File logging added to all three services under `logs/` (gitignored, appends across
restarts, rolls at 10 MB).

⏳ **Deferred:** `deliver()` still logs nothing, so the trace stops at "message received" —
the SMS send and the `SENT` transition are invisible. The MDC key is a string literal in
~6 places; a typo fails **silently**. Notification's provider `RestClient` has no
interceptor, so the outbound provider call is untraced.

### ▶ IN PROGRESS — ledger transaction types (decided, not yet built)

**The gap:** `ledger` records `entry_id, debited, credited, amounts, idempotency_key,
created_at, reverses_entry_id` — and **no business meaning**. Every row is "a transfer".
This blocks statements, analytics and the deferred AI feature, so it is a **prerequisite
for history #4**, not a refinement.

**Rejected — deriving the type from account numbers at runtime.** It is *possible* today
(`001`=topup, `002`=outward, `003`=suspense, `004`=bill) but: it leaks wallet internals
across a service boundary (history #4 via CDC would hardcode "account 004 = bill"), it
cannot distinguish flows that share a shape, it discards intent that was known at write
time, and every consumer re-implements the same mapping and drifts.

**DECIDED — money-movement kinds, not products.** The wallet never learns what a "bill" is.

### The seven transaction types

| type | movement | written when | endpoint |
|---|---|---|---|
| `DEPOSIT` | `001` → customer | money enters the platform (gateway confirms a top-up) | `/top-up` |
| `WITHDRAWAL` | customer → `002` | money leaves the platform toward another bank | `/withdraw` |
| `TRANSFER` | customer → customer | a completed P2P inside the platform | `/betweenWallets` |
| `HOLD` | customer → `003` | funds earmarked, outcome not yet known | `/hold` |
| `SETTLEMENT` | `003` → `004` | the beneficiary confirmed — the hold is discharged **outward** | `/settle` |
| `RELEASE` | `003` → customer | the beneficiary declined — the hold is discharged **back** | `/revers` on a `HOLD` |
| `REVERSAL` | opposite of the original | an undo of anything that was **not** a hold | `/revers` on anything else |

**Why each exists — the distinctions that are load-bearing:**

- **`HOLD` vs `TRANSFER`.** A hold models *uncertainty over time*: the customer has committed but the outcome is unknown, so the money belongs to neither party and something must later resolve it. A transfer has no such gap — it is settled the instant the row commits. **Do not route P2P through suspense**: it would split one row into two, lose the per-row conservation guarantee (`debited + credited = 0`), and invent a stranded-funds failure mode that cannot exist today.
- **`SETTLEMENT` vs `RELEASE`.** The two ways a hold ends — outward to the beneficiary, or back to the customer. Both consume the hold; they differ only in destination. Keeping them distinct is what makes
  `SUM(HOLD) − SUM(SETTLEMENT) − SUM(RELEASE)` = **money currently in suspense**, answerable from the wallet alone, without asking the bill service.
- **`RELEASE` vs `REVERSAL`.** `RELEASE` is deliberately narrow: *only* money leaving suspense. Broadening it to mean "any undo" would pull reversed transfers and reversed deposits — which never touched suspense — into the held-money query and silently corrupt it. `REVERSAL` therefore covers every other undo, with `reverses_entry_id` saying *what* was undone. Rejected alternatives: one `REVERSE_DEPOSIT`-style value per reversible kind (doubles the vocabulary, most values never occur), and typing a reversal as its original kind (then `count(TRANSFER)` silently counts undos unless every query remembers `WHERE reverses_entry_id IS NULL`).
- **`SETTLEMENT` is a liability movement, not an outbound payment.** `004` is "money we owe billers", not the biller's bank. Real payout would be a separate netting run — out of scope, but the model is already shaped correctly for it.

**Assignment rule:** every endpoint hardcodes its own type server-side — unforgeable, and the
controller never handles a `TransactionType`. `/revers` is the sole exception: it **derives**
the type from the entry being reversed (`HOLD → RELEASE`, else `REVERSAL`), so it must load
the original first.


Immediate payoff: `SUM(HOLD) − SUM(SETTLEMENT) − SUM(RELEASE)` = money currently held.
On today's data that is **4**, while the bill service reports **5** bills `Reserved` —
a discrepancy worth chasing once the column exists, and a question the ledger could not
even be asked before.

**DECIDED — `varchar` + `CHECK`, not an int code.** An int makes the database unreadable
(`3` means nothing), forces every consumer — including CDC — to carry the mapping, and if
it is an enum ordinal, reordering silently rewrites history. The "freedom to rename"
argument conflates two things: the **identifier** (`BILL_PAYMENT`, ~never changes) and the
**display label** ("Bill Payment" / "دفع فاتورة", changes often and per language). The
label is a presentation mapping in #4 regardless; the int buys nothing and costs legibility.

**DECIDED — type assigned server-side from dedicated endpoints**, not a caller-supplied
field. Unforgeable, and it gives each operation its own preconditions (a `HOLD` can fail on
insufficient balance and is customer-facing; a `SETTLEMENT` should never fail on balance,
so a failure there is an ops alarm). Add `/v1/transfer/hold` and `/v1/transfer/settle` to
`LedgerEntryController` — **generic names, not a `BillController`**, which would put bill
vocabulary back into the wallet and undo the decision above. `/revers` needs no sibling:
reversing a `HOLD` *is* a `RELEASE`, so the type derives from the target entry.

**DECIDED — no `purpose` column and no `counterparty_ref`.** Both are the read model's job.
History #4 consumes wallet *and* bill events and joins them on the correlation id, so
`HOLD` + a bill event carrying the biller = a bill payment, with product meaning owned by
the service that knows it. Copying the biller code into the ledger would create two sources
of truth for one fact.

✅ **Wallet endpoints done and verified end to end.** `/hold` and `/settle` added to
`LedgerEntryController`; every endpoint assigns its own type server-side except `/revers`,
which derives it from the entry being reversed. `/settle` takes **only** the hold's
`entryId` — the wallet supplies the beneficiary account *and reads the amount from the
hold*, so settling a different amount than was held is impossible by construction rather
than merely forbidden.

Verified against a live wallet: `HOLD` → `SETTLEMENT` (with `settles_entry_id`), a second
settle of the same hold → **409** from `uq_entry_discharged_once`, settling a non-hold →
**400**, and `/revers` on a hold → `RELEASE` with `reverses_entry_id`. Both discharge paths
land in the correct column, so the held-money query stays meaningful.

⚠️ **Bug found while testing, now fixed:** the catch-all `@ExceptionHandler(Exception.class)`
was swallowing Spring's own well-classified exceptions and returning **500** for every
client mistake — a blank field, a missing `Idempotency-Key`, malformed JSON. Callers could
not distinguish "your request was bad" from "the wallet is broken", which is exactly the
distinction the bill service branches on. Added explicit handlers for
`MethodArgumentNotValidException`, `MissingRequestHeaderException` and
`HttpMessageNotReadableException`, all returning **400**. Two related lessons: an
`@ExceptionHandler` returning **`void` yields 200 OK**, silently converting a rejected
operation into a reported success; and a client-facing `detail` must be built from
`getBindingResult().getFieldErrors()`, never from `getMessage()`/`getParameter()`, which
dump controller signatures, DTO class names and Spring's internal message codes to the caller.

✅ **A 409 now carries what the caller needs to recover.** Found while wiring the bill
service: `/settle` can return 409 for **two opposite reasons** — a duplicate idempotency key
(the same request replayed, safe to treat as success) or `uq_entry_discharged_once` (the
hold was discharged by something else). Indistinguishable by status alone, and the second
has a dangerous case: a hold that was **released** (money already back with the customer)
would be read as "settled" and the bill marked `Paid`.

Deferring it to the EOD sweep does not work — the sweep would re-inquire, re-capture, hit
the same 409, and loop forever, leaving the bill permanently `Reserved`. The poison-row
problem, in the bill service.

Fix: `settle` pre-checks `findEntryByHoldId` and throws `HoldAlreadyDischargedException`
carrying **the hold id, the discharging entry id, and its type**, surfaced as
`ProblemDetail` properties. The bill service reads `dischargeType` and reconciles in the
same call — `SETTLEMENT → Paid`, `RELEASE → Rejected` — with no extra round trip and no
biller inquiry. Verified both paths live.

**Principle:** an error response should carry what the caller needs to recover. A bare 409
says "no" and leaves them stuck; one that says *"released by entry X"* tells them exactly
what to do.

**Two supporting lessons:** the pre-check produces a *good error*, the unique index provides
the *guarantee* — check-then-act races, so both are needed. And the query uses `@Query` with
`coalesce(...)` rather than a derived `...OrSettlesEntryId` method, because only the
`coalesce` form matches `uq_entry_discharged_once`; the derived version would seq-scan. The
integrity constraint doubles as the lookup index.

✅ **Bill service now speaks in domain terms.** `reserve` → `/v1/transfer/hold`,
`capture` → `/v1/transfer/settle` taking the **hold's entry id** (the value `reserveFunds`
already stored and used to discard). `SUSPENSE_ACCOUNT`, `Biller_ACCOUNT` and the generic
`transfer()` are deleted — the bill service no longer knows the wallet's account numbering.

**Every branch out of `capture` reaches a terminal bill state**, which is what stops the
sweep looping: 409 with no properties (duplicate key) and 409 with `dischargeType
SETTLEMENT` both **return normally** — same outcome as success, and an exception whose
handler does nothing different is a liability, because forgetting to catch it strands the
bill. Only `RELEASE` throws. A 400 throws `SettleRejectedException` → new **`Failed`**
status (bill-service `V2`), *not* `Rejected`: `Rejected` implies the customer was refunded
(the FAILED path reverses first), while a refused settle leaves the hold intact, so
`Rejected` would lie about where the money is.

**Verified end to end**, including the race this was all for: biller settles but the caller
times out (simulator `TIMEOUT` mode), the hold is released behind the bill service's back,
the sweep then inquires and captures → wallet answers 409 `dischargeType RELEASE` → bill
becomes **`Rejected`**. Previously `capture` swallowed the 409 and `resolve` set `Paid`
unconditionally, so the bill would have claimed payment while the customer had their money
back and the biller had never been paid.

🧹 **Stranded test data cleaned (2026-08-27).** 6 bills sat `Reserved` from sessions where
the biller was down; the simulator had no record of them so `NOT_FOUND` meant the sweep
could never resolve them. All five outstanding holds were released **through the API**
(never by editing the ledger) and the bills reconciled to `Rejected`. Sweep working set is
now empty.

⚠️ **The cleanup left a trap for the backfill.** `revers` derives its type from the target,
and four of those holds predate `V13`, so their discharges were typed **`REVERSAL`**, not
`RELEASE`. Once the backfill types those holds as `HOLD`, the held-money identity
`SUM(HOLD) − SUM(SETTLEMENT) − SUM(RELEASE)` gains +4,050 that is never subtracted.
**The backfill must type discharge rows by account movement too** (`003 → customer` is a
`RELEASE`), not merely fill nulls on holds.

*(This also proved the backfill's value: "held total" read **75** while 4,050 was genuinely
held, because untyped rows are invisible to `SUM(HOLD)` — a 98% undercount.)*

✅ **THREAD COMPLETE — the ledger is fully typed.** `V16` backfilled all 57 untyped rows
by account-movement derivation, `V17`/`V18`/`V19` contracted `transaction_type` to
`NOT NULL`. All six endpoints verified live, each producing its correct type — every value
in the vocabulary is now reachable from a running endpoint.

**Two design choices inside the backfill worth keeping:**

*No `ELSE` on the `CASE`.* An unmatched account pair stays `NULL`, so the later
`SET NOT NULL` **fails loudly** rather than letting a mislabelled row through. `ELSE
'TRANSFER'` would have been the `routing_key` trap again — a plausible guess, quietly
believed. Fail-closed for free.

*`TRANSFER` detected via `wallet.is_internal`, not account-number patterns.* Matching on
`'0000000000%'` would have re-introduced the exact coupling this whole thread removed. Took
three attempts to land: the first subquery was **uncorrelated** (asked "do any internal
wallets exist?" → always true), the second was correlated but omitted `is_internal` (asked
"does a wallet exist for either side?" → always true, guaranteed by the FKs). The test for
a correlated subquery: *would it give a different answer for a different outer row?*

**The cleanup trap was real and is closed:** four discharge rows typed `REVERSAL` (their
targets predated V13) were corrected to `RELEASE` in the same migration. Held-money identity
now reads **0**, which is correct — every outstanding hold was released during cleanup.

⚠️ **Two ways to measure held money now disagree, and only one is right.**
`SUM(HOLD) − SUM(SETTLEMENT) − SUM(RELEASE)` = **0** ✅, while the reference-based query
(*"holds nothing discharges"*) still reads **6,025** — four pre-constraint rows took money
out of suspense without recording `settles_entry_id`, and that link cannot be
reconstructed. **Counting by type survives incomplete history; counting by reference only
works where the reference was always written.** Build reporting on the type identity.

🧹 **Optional tidy, not done:** `ck_ledger_transaction_type_not_null` and
`reverses_entry_id_unique` are now scaffolding — superseded by `attnotnull` and
`uq_entry_discharged_once` respectively.

✅ **Notification ownership decided — `TransactionType.isCustomerFacing()`.** The wallet
now suppresses events for the whole **hold lifecycle** (`HOLD`, `SETTLEMENT`, `RELEASE`);
`DEPOSIT`, `WITHDRAWAL`, `TRANSFER` and `REVERSAL` still notify.

**The rule: the service that knows *why* the money moved owns the message.** The wallet can
only say "you received 55 SAR"; the bill service can say "your SEC bill couldn't be paid —
you've been refunded". So `bill.rejected` belongs to the bill service, not to the wallet's
`RELEASE`.

Before this, a bill payment notified the customer at **reserve** — and that message could be
outright wrong, since a declined biller returns the money that was already announced as
sent. Now a full bill payment (`HOLD` + `SETTLEMENT`) and a full refund (`HOLD` + `RELEASE`)
each produce **zero** wallet events. That is a gap made *visible*, not created: it is exactly
what the bill service's own events will fill, and it guarantees the customer gets **one**
message per bill rather than two.

Implemented as a method on the enum rather than an exclusion list in `WalletService`,
because a `switch` over all seven constants with **no `default`** means adding an eighth type
fails to compile until someone answers the question. An exclusion list would have let a new
type inherit "notify" silently.

⚠️ **Consequence of the accepted `HOLD`-means-bill risk, now concrete:** the wallet cannot
tell a bill-originated hold from any other — by design, since it never learns what a bill is.
So **all** releases are suppressed, and any future flow that creates holds inherits the
obligation to publish its own events. A hold released by an ops correction currently
notifies nobody. Rejected: passing a "notify" flag into `/revers` (puts a notification
concern into a money API, and a caller can get it wrong).

✅ **DECIDED — `Failed` publishes no customer event.** The bill outbox carries exactly two
types: `bill.payment.paid` and `bill.payment.rejected`.

`Failed` means the wallet refused the settle as invalid — never a business outcome, always
the bill service having asked for something impossible. It requires manual intervention, and
a customer cannot act on "we do not know where your money is"; such a message invites a
support call that cannot be answered. It gets an **ops signal** (ERROR log, later an alert),
not an SMS.

⚠️ **`Failed` is reachable, and the path is crash recovery — worth testing in Phase 7:**

    reserve → wallet commits the hold
            → bill service crashes before saving the bill
    retry   → same reserve key → wallet returns 409 duplicate
            → WalletClient.reserve's onStatus(409) swallows it
            → .body(TransferResponse.class) has no TransferResponse to map (the
              duplicate response body is EMPTY — verified)
            → entryId null → bill saved Reserved with entry_id null
            → capture(null) → 400 → Failed

The empty-body-on-duplicate behaviour was confirmed against the running wallet. Three bills
already carry a null `entry_id` (those are legitimately from the reserve-declined path, but
they show the column does go null). **The 409 swallow in `reserve` deserves the same
treatment `capture` got** — it currently discards a response it then tries to deserialise.

🔨 **IN PROGRESS — bill outbox.** ✅ `V3` (table + partial index) and `V4` (`cif` column)
applied · ✅ entity, repository, `PaymentEvent` payload · ✅ **cif carried from the wallet's
hold response** · ✅ **transactional write on every resolved outcome**.
⏳ **NEXT: the relay job** — the bill service has no AMQP wiring at all yet (no
`spring-boot-starter-amqp`, no `spring.rabbitmq.*`, no exchange bean), so that is the setup
step before the job itself. Then the notification-side binding and message text.

**cif resolved (b): the wallet returns it at reserve time.** `/hold` gets its own
`HoldResponse` rather than a `cif` field on the shared `TransactionResponse`, because "the
cif" is ambiguous on the other five endpoints (a P2P has two customers, a settlement none).
Stored on the `bill` row because the outbox event is written in `resolve`, possibly minutes
later via the sweep, by which point the reserve response is gone — the same reason the
correlation id is a column. Rejected: deriving `cif` from the wallet number by substring
(puts the wallet's numbering format in the bill service, fails silently if the prefix width
changes), and having notifications resolve it (couples delivery availability to the wallet,
adds a failure mode to a path with a retry budget, repeats the lookup on every retry).

**`cif` is not a constructor argument** — it is unknown when `createPayment` runs and
arrives with the hold response alongside `entryId`. A constructor should take what is known
at construction.

⚠️ **Pre-existing cross-service bug found by exercising the reverse path:** bill sent
`original_entry_id` while the wallet expects `originalEntryId`, so Jackson read **null** and
the wallet answered 404 for an entry with a null id. The resulting
`HttpClientErrorException` is *not* caught by `payBiller` (which catches only
`HttpServerErrorException` and `ResourceAccessException`), so it escaped into `@Async`, was
dropped by Spring's uncaught handler, and left the bill `Reserved` to be swept forever — a
poison row. **Mocked tests cannot catch this class of defect**, since they stub the client
and never check the wire format. Still open: `payBiller` catching only two specific
exception types means any *other* HTTP error silently strands a bill.

**Verified all four paths:** `Paid` → `bill.payment.paid`; declined biller →
`bill.payment.rejected`; biller has no record → stays `Reserved`, no event; and the wallet
writes **nothing** for any of them. One row, one eventual notification per bill payment.
During testing one bill was recovered by the **EOD sweep**, carrying a
`reconciliation-bills-` correlation id rather than the original request's — the run-id
versus flow-id distinction visible in real data.

**Transaction boundary — decided, and the learner was right.** `resolve` must **not** be
`@Transactional`: it makes a synchronous HTTP call to the wallet with a 2-second timeout
that is not ours, and wrapping it would tie a pooled DB connection to someone else's
latency. But the outbox guarantee still needs the status change and the event row to commit
together. The resolution is in the **ordering** — the HTTP call happens *first*, then the
writes:

    1. walletClient.capture(...)     ← HTTP, OUTSIDE any transaction
    2. bill.setStatus(...)           ┐
    3. billRepository.save(bill)     ├ one @Transactional method
    4. write the outbox event        ┘

⚠️ Extract 2–4 onto a **separate bean**. `this.persistOutcome(...)` is self-invocation, which
bypasses the proxy and silently gives no transaction at all — §9, hit three times already.

**Open question for the payload:** the notification service needs a `cif` (contact details
are looked up fresh at delivery time, never snapshotted). `Bill` stores `wallet_number`, not
`cif`. Either derive it (wallet numbers are `%02d` + cif) or carry the wallet number and let
notifications resolve it — decide deliberately.

⚠️ **Known residual risks, accepted:**
- `HOLD` currently means "bill" only because the bill service is the sole caller. Add
  merchant payments later and it becomes ambiguous, with no way to re-derive history.
- **Nobody records the destination bank** for a withdrawal — one generic `Outward Transfer`
  account for all outbound money. Fix is per-bank internal accounts (the credited account
  identifies the bank), not a new column. Parked deliberately.

**LATE ADDITION — `settles_entry_id` + a discharge invariant.** Found while checking
whether settlements were traceable: `RELEASE` rows link back via `reverses_entry_id`, but
**`SETTLEMENT` rows link to nothing** — `WalletClient.capture(amount, idempotencyKey)`
never passes the hold's entry id, even though `BillService.resolve` is holding
`bill.getEntryId()` right there. So the wallet's own ledger cannot say which hold a
settlement discharged; the relationship exists only in the bill service.

Fix mirrors the existing pattern: `settles_entry_id` alongside `reverses_entry_id`. But a
plain `UNIQUE` on each leaves a hole that matters more — **nothing stops a hold being both
settled *and* released**, two rows each satisfying its own constraint, paying the biller
*and* refunding the customer from one hold. **Money created.** Closed with a single
expression index instead:

```
CREATE UNIQUE INDEX ... ON ledger (coalesce(reverses_entry_id, settles_entry_id));
```

Both discharge paths now compete for one slot; the second fails at the database. Ordinary
transfers have both columns null and `coalesce` yields null, which never conflicts in a
unique index. **Proven**: attempting to settle an already-released hold returns
`duplicate key value violates unique constraint`. The golden rule is now structural rather
than something the EOD sweep and idempotency keys must be careful about. This subsumes
`V9`'s `reverses_entry_id_unique` (redundant, harmless to keep).

*(Considered and dropped: adding `correlation_id` to `ledger`. `settles_entry_id` is
stronger for this problem — a ledger-internal relationship rather than a debugging
breadcrumb — and it carries the uniqueness invariant a correlation id cannot.)*

**Build order:** ✅ `V13` (`transaction_type` + `settles_entry_id` + `uq_entry_discharged_once`)
✅ `V14` (`ck_one_discharge_kind`) ✅ `V15` (seven-value vocabulary) ✅ **wallet Java side done**
→ ✅ bill service switched to the new endpoints → ✅ **V16 backfill** → ✅ **V17/V18/V19
contract to NOT NULL** → **THREAD COMPLETE** —
backfill by derivation (correct **once**, in a migration, never at runtime; use an honest
`UNKNOWN` for unclassifiable pairs rather than defaulting to `TRANSFER` — the `routing_key`
lesson) → contract to `NOT NULL`.

◀ **NEXT — the ordered path to Phase 7 (decided 31 Aug, rev 10):**

**1. ~~Finish bill → notification~~ ✅ DONE (3 Sep).** AMQP wiring · relay job ·
`bill.payment.*` binding · `NotificationMessage` enum for the text.

**Verified end to end:** one bill payment → **exactly one** customer notification, routing
key `bill.payment.paid`, carrying the correlation id of the originating HTTP request.
**Seven log lines across three JVMs, five threads, a database and a broker — one grep.**
The relay's own line is bracketed `bill-relay-…` yet the grep still finds it, because the
*business* id is in the message text and on the message: the two-ids design working in
practice.

Message text moved onto an enum because there are now **four** routing keys and a two-way
ternary cannot express four outcomes — a paid bill would have rendered "you received a
transaction!", and a rejected one the same. Routing keys contain dots so they cannot be
constant names; they are a **field**, with a static lookup from wire value to constant that
**throws** rather than returning a fallback (an unrecognised key means a producer published
something this service was never updated for — silently shipping generic text to a customer
is the failure worth avoiding).

⚠️ **Two stranded bills observed, and both are correct.** A payment whose biller call never
landed is **invisible to the sweep**: it inquires, gets `NOT_FOUND`, and does nothing —
forever. **The sweep can only resolve bills the biller knows about.** Good Phase 7 material,
and an argument for a "reserved longer than N" alert that the sweep cannot itself provide.

⚠️ **Live proof of the silent-drop failure mode (2 Sep).** The bill relay publishes
correctly, the outbox row reads `sent_at`, and **nothing consumes it** — the queue binds
`wallet.money.*` only, so `bill.payment.paid` is accepted by the exchange and routed
nowhere. The broker's counters showed it: **`publish_in 79` vs `publish_out 72`** — seven
messages in, never out, no error anywhere.

Two things follow. **`publish_in − publish_out` is a monitorable signal** (a persistent gap
= unroutable messages) and belongs on the Phase 8 dashboard. And it writes itself as a
**Phase 7 scenario: break a binding, watch the outbox happily report success.** The loud
version is publisher confirms + the `mandatory` flag, which returns unroutable messages to
the sender — deliberately not built yet.

### ✅ DECIDED (2 Sep) — one exchange, one queue, two bindings  ·  *ADR candidate*

**Exchange and queue are two separate decisions.** Splitting them dissolves the argument:

**ONE topic exchange (`quickpay.events`), producers own routing-key NAMESPACES.**
`wallet.money.*` and `bill.payment.*`. Rejected exchange-per-producer: the argument for it
was that history #4 could then plug in with zero producer changes — but that is equally
true of one exchange, and the shared exchange is *stronger*, because it also gives zero
**consumer** changes when a new producer appears. With exchange-per-producer, #4 must know
there are two exchanges today and be edited when a third arrives — more coupling, not less.
(Exchange-per-context is real at org scale — per-exchange permissions, alternate-exchange
policies, blast radius — but that is gold-plating here.)

**ONE queue, TWO bindings.** Handling is identical, so one consumer, one retry path, one
`processed_events` table. **Separate queues were considered and rejected as pre-solving** —
the reviewer argued for them on Phase-7 observability grounds (independent depth = "is the
bill flow backing up or the wallet flow?") and was talked out of it by the same rule that
governs the circuit breaker: **fixes get earned.**

⚠️ **The honest risk, deliberately left in: head-of-line blocking.** A bill-event flood or a
poison message starves wallet notifications. **This is Phase 7 scenario material** — flood
bill events, measure wallet notification latency, split the queues only if the evidence
demands it. Note `@RabbitListener(queues = {a, b})` means separate queues would *not* have
required separate listeners: queue separation buys independent depth and purge; listener
separation buys independent concurrency. Two different things.

⚠️ **Consequence — `processed_events` is now a shared dedup table across two producers.**
Both outbox `event_id`s are **UUIDs**, so there is nothing to collide today (a raised
concern about `bigserial` ids does not apply — verified). But if either producer ever
switched to a sequence, the collision would be **silent** and would present as
"notifications randomly not sent". Any new producer must keep globally-unique event ids.

⏳ **Still to do on the notification side:** the `bill.payment.*` binding, and message text —
`deliver()` resolves it from a **two-way** check on `wallet.money.sent`, and there are now
**four** routing keys. A two-way ternary cannot express four outcomes; same shape as
`isCustomerFacing()` on the wallet's enum.

**2. Phase 7 — SABOTAGE, September, on the THREE-service system.**

🔄 **DECISION REVERSED — the four-services gate is DROPPED.** The gate said "sabotage once,
after all four services exist". That was defensible only while #4 was near. **v2.2 parks
history #4 to the Kafka project (~May 27)**, so the gate would delay Phase 7 by nine months
to wait for a *read model* — while the whole distributed-transaction surface is already
built and ready to break: saga with compensation, transactional outbox, broker,
at-least-once delivery, idempotent consumer, two reconciliation jobs, and correlation IDs
to read it all with. **#4 gets its own small sabotage addendum when it is born** (projection
lag, CDC lag — genuinely different scenarios, and genuinely later).

**3. Phase 8 — k6 load test.** Requires a written breaking-TPS and bottleneck prediction
before the first run.

---

### Topic #5 (circuit breakers) closes INSIDE Phase 7, not before it

**DECIDED: sabotage-driven, not pre-built.** There is no Resilience4j in the repo — no
retry-with-backoff, no circuit breaker. Building one now would be studying a topic in the
abstract, which the standing rules forbid, and gold-plating, which rule 6 forbids.

Instead it is a Phase 7 scenario that *earns* the fix:

> **Scenario — biller (or provider) down for five minutes.** Predict first, then watch
> every call burn its full timeout. Add the Resilience4j breaker as the fix that scenario
> produced. Topic #5 then closes honestly, with evidence.

⚠️ **Prediction hint — the EOD sweep is serial.** `fixedDelay` waits for completion and each
`inquire` burns the full 2s timeout, so with *N* stranded bills one pass takes *N* × 2s. At
~30 bills that is a minute, and **the sweep stops keeping up with its own interval** — a
more visible failure than a slow request, and precisely what fail-fast + half-open fixes.
Worth predicting the `@Async` pool behaviour too.

---

**Feature-complete is now THREE services + the bill event flow** (not four). History #4 is
no longer a Stage-1 dependency; when it does arrive it will need the correlation id carried
through whatever CDC path is used — Debezium reads the WAL and sees only columns, which is
another argument for the id living *in the row* rather than in memory.

Shape: `@Scheduled`, **two queries** — one per channel, each matching one of V1's partial
indexes — merged by `message_id` so a row needing both channels is not processed twice and
`attempts` is not double-incremented. Calls `NotificationService.deliver(...)` as its
**third caller** (new-event path and this job share it).

**Predict first:** with `maximum-retries = 5` and the simulator at a 0.3 failure rate,
how many rows still have `sms_status = false` after the job has run enough times to
exhaust retries — and what should happen to a row that hits the cap?

⚠️ **Cleanups riding along** (do not let these rot): `ProcessedEvent`'s 10-arg positional
constructor is a hazard — adjacent `boolean` / `LocalDateTime` params are silently
interchangeable. The listener's else-branch duplicates the customer lookup instead of
calling `extractCustomerFromMessage`, and carries dead fields (`notificationProviderClient`,
`MAXIMUM_RETRIES`) plus unused locals.

**Also still open:** the bill service does not publish `BillPaid`/`BillRejected` yet, so
notifications only sees wallet events. And a bill reserve emits a wallet event too, so a
bill payment will produce a wallet notification *and* (later) a bill one — suppression is
a notifications-side policy call.

*(Open decisions #1 and #2 were both resolved 2026-08-01 — see §5 and ADR-0005.)*

---

<details>
<summary><b>✅ CLOSED 2026-08-01 — bill-service tests (was the previous NEXT ACTION)</b></summary>

**7 tests green, committed `8d1616b`.** Written one at a time with review between each.

| # | Test | |
|---|---|---|
| 1 | `createPayment` new key — write-ahead, no client touched | ✅ |
| 1b | `createPayment` replay — same record, no second row, stored amount wins | ✅ |
| 2 | `reserveFunds` accepted — `Reserved`, entry_id stored, derived `r`-key | ✅ |
| 3 | `reserveFunds` declined — `Rejected`, entry_id null | ✅ |
| 4 | `resolve` PAID — capture once, reverse never, `Paid` | ✅ |
| 5 | `resolve` FAILED — reverse once **with the reserve's entry_id**, capture never | ✅ |
| 7 | double-resolve — capture must fire exactly once | ✅ (after fixes) |
| 6 | UNKNOWN reaction | ⏭️ **skipped, deliberately** |
| 8 | Sweep | ⏭️ **skipped, deliberately** |

**Why #6 and #8 were skipped (a decision, not drift):** both cover behaviour already
proven by hand on 1–2 July, so they buy regression cover rather than knowledge, and the
marginal learning was nil once `thenReturn` / `thenThrow` / `verify` / `never()` were
understood. #6 additionally needs a `SyncTaskExecutor` `@TestConfiguration` to make
`@Async` deterministic. Revisit if either path changes.

**What test #7 found — the payoff of the whole exercise.** It failed on first run
(`TooManyActualInvocations: wanted 1, was 2`) and drove three production fixes:
1. `resolve` had **no re-entry guard** — it captured twice. Prediction was correct: the
   only protection was the wallet's `c`+paymentId key downstream. Guard added.
   ⚠️ The guard does **not** close the true race — the status check and the money move
   are separated by a biller call that can block up to 60s, and the sweep holds a stale
   detached snapshot — so **the wallet's `UNIQUE(idempotency_key)` remains the real
   guarantee**. This is the golden-rule-in-the-database philosophy paying off.
2. **A 409 was being treated as a failure.** It means "already applied" = success. It
   surfaced as `HttpClientErrorException`, uncaught by `payBiller` *and* the sweep.
   Now handled on both `capture` and `reverse` in `WalletClient`.
3. **The sweep had no batch isolation** — one failing bill aborted the whole run, in the
   one component whose entire job is recovery. Now per-bill isolated (specific catches
   kept for meaningful logging, plus a catch-all backstop).

Money was never at risk at any point — the wallet's UNIQUE constraint held throughout.

</details>

---

## ⏹ DEFINITION OF DONE (rev 2 — DRAFT: confirm or rewrite in your own words, then delete this marker)

QuickPay is **done** when all three are true:
1. All **seven business requirements** are ✅ — or explicitly closed out in the sponsor
   decisions log (auth's "close it explicitly" clause now has somewhere to point).
2. The **sabotage log** holds ~12 scenarios, each with a written prediction and every
   surprise explained.
3. The **load report** names the breaking TPS on P2P and at least one fix that moved it.

*(Shape lifted from the playbook's own Q1–Q2 milestones. Learning projects don't ship —
without this line they dissipate.)*

---

## 1. What this project is

A learning lab: a miniature SAR digital-wallet platform, built end-to-end and then
deliberately broken. It mirrors the learner's real job (designing transaction flows).

**The learner (Abdulaziz) writes ALL application code.** The AI is a blunt senior
reviewer + explainer, and may only scaffold: the mock simulators, Docker Compose,
build config (`pom.xml`) when asked, and docs. Full rules in `CLAUDE.md`.

### Non-negotiable working rules
1. **The golden rule** — money is never created or destroyed, and the *database*
   enforces it (constraints), not trust in application code.
2. **Predict-then-run** — before any test or migration run, the learner writes the
   predicted outcome first, then runs and compares. This is the single highest-value
   habit in the project; it has caught many real bugs.
3. **No gold-plating** — max 4 services, no Kubernetes, no Spring Cloud.
4. **Review-gated phases** — no phase starts before the previous deliverable passes review.
5. **Applied migrations are immutable** (ADR-0003) — never edit one that has run; fix forward.

---

## 2. Target state (from `quickpay-project-brief.md`, docs branch)

Sponsor = a Riyadh fintech founder. Seven business requirements:

| # | Requirement | Status |
|---|---|---|
| 1 | Accounts — customers hold a SAR wallet (thin auth) | 🟡 wallets ✅, **no auth** |
| 2 | Top-up via async gateway; callbacks may duplicate or never arrive | ✅ done (HMAC webhook + idempotency) |
| 3 | P2P transfer, instant; app auto-retries after 5s | ✅ done (idempotency key makes retry safe) |
| 4 | Bill payment via slow (≤60s) flaky biller; never lose customer money, never show "paid" falsely | ✅ **done 2026-07-01** |
| 5 | Notifications on every completed transaction; channel fails often, must never block a payment | ⛔ **not started** |
| 6 | History + monthly statement per customer | ⛔ not started |
| 7 | Golden rule — every movement explainable, finance will audit | ✅ structurally enforced |

**Non-functional:** P2P targets 500 TPS at peak; every payment op safe to retry; full audit trail.
**Stretch:** nightly reconciliation of gateway records vs wallet records.

### Hard constraints
- Spring Boot 3 + PostgreSQL, **one database per service**
- **RabbitMQ** for async messaging (Kafka is a different project)
- Docker Compose only — no K8s, no Spring Cloud
- **Maximum 4 services** (currently using 2)
- External gateway + biller are mocks (AI-built, permitted scaffolding)

---

## 3. Baseline — what exists today

### Modules
| Module | Port | DB | State |
|---|---|---|---|
| `wallet-service` | 8080 | `wallet` @ 5432 | ✅ complete, 8/8 tests green, in CI |
| `bill-service` | 8081 | `bill` @ 5433 | ✅ feature-complete, ✅ **7 tests green** (in CI; 2 deliberately skipped) |
| `scaffolding/gateway-simulator` | 9090 | — | mock payment gateway (HMAC webhooks) |
| `scaffolding/biller-simulator` | 9091 | — | mock biller (force PAID/FAIL/SERVER_ERROR/TIMEOUT) |

Simulators mock *external* systems and do **not** count against the 4-service budget.

### Wallet service (the money core)
- **Two-leg single-row ledger**: one row holds both sides; `CHECK(debited + credited = 0)`
  makes a one-sided money move impossible to commit. (ADR-0001)
- Money = BIGINT minor units. Ledger is **append-only** — reversals are new rows.
- Idempotency = client-supplied key + `UNIQUE(idempotency_key)`.
- Concurrency = pessimistic `FOR UPDATE`, wallets always locked in a consistent order
  (deadlock prevention by lock ordering, not timeouts).
- Reconciliation job asserts `balance == SUM(ledger legs)` per wallet.
- Migrations **V1–V10**.

**System accounts:**

| Number | Name | `is_internal` | `allows_negative` | Role |
|---|---|---|---|---|
| `000000000001` | Inward / TopUp | ✅ | ✅ | money source (negative by design) |
| `000000000002` | Outward | ✅ | ❌ | withdrawal sink |
| `000000000003` | Suspense | ✅ | ❌ | holds reserved funds mid-bill |
| `000000000004` | Biller settlement | ✅ | ❌ | receives captured bill funds |

Conservation invariant: `inward + customers + suspense + outward + biller = 0`.

#### Wallet baseline — settled vs still open (2026-07-30)

**The wallet's money model is BASELINED and will not be redesigned.** Settled: the
two-leg ledger and DB-enforced conservation, idempotency, lock ordering, the
reconciliation job, the four system accounts and the `is_internal`/`allows_negative`
model, reserve/capture/reverse, V1–V10, 8/8 tests in CI. Nothing remaining on this plan
requires rethinking any of it — future work builds *on top*.

**But the wallet service is NOT closed.** Four planned items will reopen its code:
1. **Notifications (req 5)** — the wallet must *publish an event* when money moves
   (RabbitMQ publisher, likely an outbox). New code inside the wallet, not just a new service.
2. **Load test (Phase 8)** — targets P2P at 500 TPS, which *is* the wallet; expect index,
   pool and lock-contention work.
3. **Sabotage (Phase 7)** — aimed largely at the money core; expect findings.
4. **Auth (req 1)** — the wallet API is currently wide open (see Bucket D).

Plus two smaller ones: **history/statement** may live in the wallet (undecided — that's
the service-budget call), and **V9's `UNIQUE(reverses_entry_id)` now rides with
bill-service test #7** (see NEXT ACTION).

### Bill service (the saga)
```
create (Pending, write-ahead, COMMITTED first)
  → reserve  (customer → suspense)         → Reserved
  → call biller →  PAID    → capture (suspense → biller) → Paid
                   FAILED  → reverse (suspense → customer) → Rejected
                   UNKNOWN → leave Reserved (do NOT guess)
  → EOD sweep re-inquires stranded Reserved bills and finishes them
```
Key properties: **write-ahead intent**, **move money first / flip status last**,
`@Async` so the slow biller never blocks the customer, and `resolve(bill, result)` —
one shared method both the live path and the sweep funnel through.

**Two identifiers, deliberately distinct:**
- `billReference` — issued by the biller, on the customer's bill. Answers *which bill*.
  Reused across attempts → **never** dedupe on it.
- `paymentId` — generated per payment record. The biller's dedup/inquire handle, and
  the seed for wallet keys (`r`/`c`/`v` + hyphen-stripped id, 33 chars to fit `varchar(36)`).
- Inbound `Idempotency-Key` header (client-supplied, UNIQUE) → one request = one record.

**All four biller outcomes verified live:** PAID→capture, FAILED→reverse,
UNKNOWN(503)→parked, UNKNOWN(timeout)→**recovered by the sweep**.

---

## 4. Phase status (brief's 0–8 plan)

| Phase | Deliverable | Status |
|---|---|---|
| 0 | Sponsor interrogation → decisions log | ⛔ skipped (log still empty) |
| 1 | Data-ownership map | ⛔ skipped |
| 2 | Decomposition + 3 sequence diagrams | 🟡 partial — bill-payment + gateway-webhook diagrams exist; no P2P diagram, no boxes-and-arrows |
| 3 | Schemas (DDL) | ✅ wallet V1–V10, bill V1 |
| 4 | Contracts (OpenAPI + message schemas) | 🟡 endpoints exist; **no OpenAPI, no async schemas** |
| 5 | Walking skeleton in Compose | ✅ done, CI runs `mvn verify` |
| 6 | Build flows | 🟡 top-up ✅ P2P ✅ withdrawal ✅ webhook ✅ bill-pay ✅ — **notifications ⛔** |
| 7 | Sabotage (~12 scenarios, predict vs outcome log) | ⛔ not started (ad-hoc only) |
| 8 | Load test (k6, find breaking TPS on P2P) | ⛔ not started |

**Key framing:** almost every gap is *additive*, not *corrective* — nothing built has to
be torn up to reach the target. The baseline conforms to the target architecture.

---

## 5. Open decisions (resolve deliberately, not by accident)

1. **⚠️ RabbitMQ divergence.** The brief lists RabbitMQ as a hard constraint. The bill
   service uses in-memory `@Async` instead (guarded by the EOD sweep). Two honest options:
   **(a) remediate** — introduce RabbitMQ for notifications (and possibly the biller step);
   **(b) change the target** — formally decide `@Async` + DB sweep is sufficient at this
   scale and log it as an architecture/sponsor decision. *Leaving it undeclared is the
   only wrong answer.*
   **→ RESOLVED 2026-08-01 — see `adr/0005-rabbitmq-for-event-fanout.md` (docs branch)
   and sponsor decisions log entry #1.** Adopt RabbitMQ for **domain-event fan-out**
   (wallet + bill publish; notifications + history consume). **Keep the bill service's
   biller trigger on `@Async` + the EOD sweep** — it is point-to-point to an *external*
   system, not a fan-out, so a broker adds a hop without adding a guarantee. The rule
   drawn: *fan-out to internal consumers goes through the broker; point-to-point calls to
   external systems do not.*
   ⚠️ **Left open by that ADR — reliable publishing.** A failed publish after a committed
   money movement loses the event permanently. Transactional outbox expected; decide it
   before the wallet publishes anything. This is now Step 0 of the notifications build.
2. ~~**Service budget.**~~ **→ DECIDED 2026-08-01. The budget is now FULL:**

   | # | Service | Owns |
   |---|---|---|
   | 1 | `wallet` | money movements — the ledger, balances, system accounts |
   | 2 | `bill` | bill-payment sagas — payment records, biller references, state machine |
   | 3 | `notifications` | delivery attempts, retry state, channel config |
   | 4 | `history` | a **read model** — its own DB, fed by wallet + bill events |

   *Notifications (#3) is forced by requirement 5 itself — "the notification channel
   fails regularly, that must never block a payment" is a decomposition instruction: a
   separate failure domain, its own data, reacting to events from **both** other services.*

   *History (#4) is a separate service because a statement is not a ledger dump — a
   customer needs "paid electricity bill 99887766", which requires joining wallet
   movements with **bill-service context**. Neither owner can produce that alone, so
   history is inherently a join across two owners → read model. Second benefit: keeps
   heavy statement queries off the money core, which matters with P2P targeting 500 TPS.*

   **Two consequences to honour when building #4:**
   - It **depends on decision #1** — a synced read model needs the event stream, so
     RabbitMQ must land first. History cannot be built before that is resolved.
   - **"Synced" means eventually consistent** — separate DB, no FKs to wallet or bill,
     no cross-service joins. A statement may briefly lag a payment. Accept it explicitly.

   **The budget is spent. Any further service breaks a hard constraint and requires a
   sponsor decision, not a quiet addition.**
3. **How much design debt to repay?** Phases 0–2 and 4 were skipped. They're the
   upfront-design muscle the brief exists to train (and map closely to TOGAF ADM
   deliverables). Legitimate to backfill, or to consciously accept the debt.
4. **Auth.** Requirement 1 mentions thin JWT auth; nothing exists. Services are wide open.
5. **AI feature — DEFERRED CANDIDATE (raised 2026-08-01).** Not in the sponsor brief;
   every "AI" mention there refers to the assistant's reviewer/sponsor role, not a
   product feature. So adding one is a **scope change** and needs a logged sponsor
   decision, not a quiet build.
   **Revisit point: once history (#4) is live.** Deferred for one decisive reason —
   every sensible version (categorisation, summarisation, natural-language query,
   anomaly flagging) reads **transaction history**, which does not exist yet and is
   itself blocked behind the RabbitMQ decision and notifications. It cannot be built
   now regardless of preference.
   **When revisited, the design is already settled:**
   - It is **read-side** → it lives **inside service #4**, not beside it. No 5th service.
   - ⚠️ **Never in the money path.** A probabilistic, slow, occasionally-wrong component
     must not gate a money movement — that is the opposite of this project's thesis that
     correctness lives in DB constraints because code can be wrong. Anomaly detection, if
     built, is **advisory**: it flags for review, it never blocks a transfer.
   - The LLM API has the **same failure profile as the biller** (slow, flaky, sometimes
     wrong), so it reuses machinery already built: read timeouts, never-block-the-customer,
     async, sweep for stragglers. Follow the existing convention and give it a **mock
     simulator** (configurable delay/failure rate, canned responses) alongside the gateway
     and biller sims — deterministic tests, no API key.
   *Judgment recorded at deferral: the project has not yet finished anything at the
   Definition-of-Done bar (no sabotage log, no load report, 2 of 7 requirements unbuilt),
   and it teaches a different subject than this project's thesis. If LLM architecture is
   the actual goal, a small dedicated project serves it better than grafting an endpoint
   onto a payments lab.*

---

## 6. Remaining work — ordered increments

Work in **one increment per session**. Do not open several at once.

### Immediate
- [x] ~~Merge `feat/biller-simulator` → `main`~~ — **done 2026-07-30 via PR #3.**
- [x] ~~Merge `test/bill-service` → `main`~~ — **done 2026-08-01 via PR #4.** `main` now
  holds the 7 bill-service tests, the three fixes they drove, and plan revs 2–5.
  Verified green on `main` after the merge: wallet 8/8, bill 7/7.
- [ ] **Rename `POST /bill/reserve` → `POST /bill/payments`** (10 min — it runs the
  whole payment; the name lies to exactly the cold reader this plan targets).
  Update the smoke-test snippet in §7 and any docs **in the same commit**.
  *(rev 2: promoted from "small deferred refinements" — gets more expensive every
  session as curls, docs and habits encode it.)*

### Bucket A — finish the build (Phase 6)
- [x] ~~Bill-service automated tests~~ — **CLOSED 2026-08-01, 7 green, committed
  `8d1616b`.** See the collapsed section under NEXT ACTION for what was covered, what
  was skipped and why, and the three production bugs test #7 uncovered.
- [ ] **◀ NEXT — Decision session** *(rev 2: pulled forward from Bucket C — open decisions #1
  and #2 gate the notifications build; "decide before building" now has a slot)*:
  learner drafts the sponsor-decision entry for the **RabbitMQ divergence** with
  reasoning; AI plays the sponsor and pushes back. Output: an entry in the sponsor
  decisions log. **Gates everything below.** *(The service-budget half of this session
  was settled 2026-08-01 — see §5 decision #2.)*
- [ ] **Notifications (req 5)** — the natural home for **RabbitMQ**. Must never block or
  fail a payment. Likely service #3.
- [ ] **History / statement (req 6)** — per the decision session's call.

### Bucket B — prove it (Phases 7–8)
- [ ] **⚠️ Traceability — correlation ids in logs. DO THIS *BEFORE* THE SABOTAGE PASS.**
  Not a nicety: sabotage deliberately breaks a system spanning three processes, an
  `@Async` thread and a scheduled sweep. Answering *"what happened to this one
  payment?"* means correlating log lines across bill-service, wallet and the
  simulator — archaeology without a shared id. It is also the one cross-cutting
  concern that is **expensive to retrofit** (touches every log statement and every
  outbound call), unlike auth which is a single filter.
  *Cheap version:* accept-or-generate an `X-Correlation-Id` at the edge → put it in
  MDC → add to the log pattern → forward as a header on both `RestClient`s.
  *Already have (accidentally):* the wallet's idempotency keys embed the paymentId
  (`r`/`c`/`v` + hyphen-stripped id), so a bill's ledger legs are already findable
  from its `paymentId`. That audit trail exists — it just isn't in the logs.
- [ ] **Sabotage pass (Phase 7)** — ~12 failure scenarios, a written prediction for each,
  run, and explain every surprise. Formalizes what's been done ad hoc.
- [ ] **Load test (Phase 8)** — k6 against P2P, find breaking TPS, fix, explain.
  Note: this reopens the **wallet** (indexes, pool sizing, lock contention).
  **rev 2 — predict-then-run applies to Phase 8 itself:** before k6 fires, write
  the predicted breaking TPS *and* the predicted first bottleneck. Seed question to
  answer in that prediction (not before): *which flow holds a hot row under load —
  P2P or top-up — and why?*

### Bucket D — cross-cutting concerns (deliberately deferred, NOT forgotten)

**Decision (2026-07-30): defer what is additive, do early what is pervasive.**

- [ ] **Auth / security (req 1) — DEFERRED to near the end, on purpose.**
  Both services are currently wide open (no authn/authz on any endpoint).
  *Why defer:* it is purely additive — a filter in front of the controllers that
  touches no schema, no ledger, no saga logic, so nothing built now becomes wrong
  when it lands. The brief itself says keep it thin (learner has built JWT before),
  so the learning value is low. And adding it now puts a token in front of every
  manual curl and every sabotage scenario — friction on the exact loop producing
  the learning.
  *Risk being accepted:* "later" can become "never." If it is still undone when the
  project wraps, **close it explicitly** against the DEFINITION OF DONE — not left to rot.
- [ ] **Structured logging** — logging exists (`logger.info/warn/error`) but is
  unstructured and uncorrelated. Pairs with the correlation-id item in Bucket B.

### Bucket C — design debt (Phases 0–2, 4)
*(rev 2: the RabbitMQ and service-budget decisions moved up into the Bucket A decision
session. The remaining backfill stays here.)*
- [ ] Sponsor interrogation + decisions log (Phase 0) — the log gets its first two
  entries from the decision session; the fuller interrogation remains open
- [ ] Data-ownership map (Phase 1)
- [ ] Boxes-and-arrows + P2P sequence diagram (Phase 2)
- [ ] OpenAPI + async message schemas (Phase 4) — the async schemas become genuinely
  useful the moment RabbitMQ lands; consider doing them with notifications

### ⚠️ Known test-coverage boundary — mocks prove *reaction*, not *plumbing*

The bill-service tests use `@MockBean` on `WalletClient` / `BillerClient`. Timeouts and
5xx are simulated by telling the mock to **throw** (`ResourceAccessException`,
`HttpServerErrorException`), which correctly tests **our reaction** — bill stays
`Reserved`/`Pending`, and `verifyNoInteractions` proves no money moved. *(rev 2: this
is now literally test #6, not just a description.)*

**These tests do NOT cover, and green CI must not be read as covering:**
- that the **read timeout is actually configured** on the `billerRestClient` bean — if
  `.withReadTimeout(...)` were deleted, the mock tests still pass while production
  hangs forever
- that `.onStatus(422, no-throw)` correctly turns a real 422 into a `FAILED` body
- that cross-service DTO field names match the other service's JSON
  (the `billNumber`/`reference`, `original_entry_id` traps)

Those are real-HTTP concerns; only **Wiremock** (a stub server that can delay or return
503) would guard them. Deliberately deferred.

**Manually verified, but unguarded against regression:** the full TIMEOUT path was
proven by hand on 2026-07-02 (biller settled PAID after the caller gave up → EOD sweep
inquired → captured → `Paid`, capture applied exactly once). *(rev 2: tests #6–#8 pin
the service-layer half of this; the real-HTTP half still waits for Wiremock. Note the
manual run proved the sequential case only — the concurrent race is test #7's job.)*

- [ ] *(optional, later)* Wiremock tests for the two HTTP clients to close this gap

### Small deferred refinements
- [ ] Bill NOT_FOUND age policy: pending > 24h → reverse + raise ops ticket (not built;
  an unused `LocalDateTime` import in `EODReconciliationJob` marks the spot)
- [ ] "OPS ticket" is currently just a log line
- [x] ~~V9 `UNIQUE(reverses_entry_id)` has no automated test~~ — **scheduled with
  bill-service test #7** (same double-movement family)
- [ ] `POST /bill/create` is a debug-only endpoint; could be removed
- [x] ~~Endpoint `POST /bill/reserve` misleading name~~ — **promoted to Immediate**

---

## 7. How to run & verify

```bash
# databases (both)
docker compose up -d                      # quickpay-wallet-db :5432, quickpay-bill-db :5433

# then start in IntelliJ:
#   QuickpayWalletApplication   -> 8080
#   QuickpayBillApplication     -> 8081
#   BillerSimulatorApplication  -> 9091
#   (gateway simulator          -> 9090, only for top-up webhook work)

# tests (no standalone mvn on this machine — use IntelliJ's bundled maven)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
"/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn" -pl wallet-service test
```

**Smoke test — full happy path** (create wallet → activate → fund → pay a bill):
```bash
# 1. wallet
curl -s -X POST localhost:8080/v1/wallets -H 'Content-Type: application/json' \
  -d '{"cif":"9900000001","walletName":"smoke"}'
curl -s -X PUT localhost:8080/v1/wallets/activate/<walletNumber>
curl -s -X POST localhost:8080/v1/transfer/top-up -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: k1' -d '{"wallet_number":"<walletNumber>","amount":5000}'

# 2. pay a bill  -> responds Reserved instantly, flips to Paid async
#    (note: endpoint rename pending — update this block in the rename commit)
curl -s -X POST localhost:8081/bill/reserve -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: k2' \
  -d '{"billReference":"BILL-1","walletNumber":"<walletNumber>","amount":2000}'
```

**Force biller outcomes** (drives the FAILED / UNKNOWN branches):
```bash
curl -s -X POST localhost:9091/simulate/mode -H 'Content-Type: application/json' \
  -d '{"outcome":"NORMAL"}'     # or FAIL | SERVER_ERROR | TIMEOUT
curl -s localhost:9091/simulate/state
```

**Inspect state:**
```bash
docker exec quickpay-wallet-db psql -U wallet -d wallet -c \
  "select wallet_number,balance,is_internal,allows_negative from wallet where is_internal;"
docker exec quickpay-bill-db psql -U bill -d bill -c \
  "select payment_id,status,entry_id,amount from bill order by created_at desc limit 5;"
```

---

## 8. Where the other documents live

- **`quickpay-project-brief.md`** — sponsor requirements, phase plan, sponsor decisions log
- **`learning-log.md`**, **`learning-playbook.md`** — method + quiz questions
- **`adr/`** — ADR-0001 two-leg ledger, ADR-0002 system accounts may go negative,
  ADR-0003 applied migrations immutable, ADR-0004 split `is_system`
- **`docs/diagrams/`** — bill-payment and gateway-webhook sequence diagrams

⚠️ **All of the above live on the `docs/adr-and-learning-docs` branch, NOT on `main`.**
`PROJECT_REPORT.txt` (a fuller prose snapshot) is on `feat/biller-simulator`.

---

## 9. Hard-won lessons (do not relearn these)

- Flyway executes the copy in `target/classes`, not `src` — rebuild before re-running a changed migration.
- Never edit an applied migration → checksum mismatch on next boot (ADR-0003).
- snake_case entity fields break Spring Data derived queries — use camelCase fields mapped to snake columns.
- `@Enumerated` with no argument defaults to **ORDINAL** — always `@Enumerated(EnumType.STRING)`, and match enum constant *case* to the DB CHECK.
- Native Postgres `ENUM` types fight Hibernate — use `varchar` + `CHECK`.
- A boolean field named `internal` → getter `isInternal()` → JPA expects column `internal`. Pin with `@Column(name=...)`.
- `@Transactional` / `@Async` **self-invocation** (`this.method()`) bypasses the proxy — call across a bean boundary.
- `Optional` is never `null` — use `isPresent()` / `orElse`.
- Cross-service DTO field names must match the *other* service's JSON exactly, or Jackson silently sends `null`.
- Idempotency keys must fit `varchar(36)` — strip hyphens, 1-char prefix.
- Two beans of the same type (two `RestClient`s) need name-matched injection or `@Qualifier`.
- A read timeout must be configured, or a hung HTTP call blocks forever and the timeout exception never fires.
- Shared Testcontainers DB + no rollback ⇒ every test needs unique idempotency keys and CIFs.
- **Flyway runs each migration file in ONE transaction** (Postgres has transactional DDL). Locks release only at commit — so bundling `ADD CONSTRAINT … NOT VALID` + `VALIDATE` in one file holds `ACCESS EXCLUSIVE` across the whole scan and destroys the lock-avoidance the split was for. **One statement per file**, at the cost of atomicity (a crash mid-sequence leaves a safe-but-incomplete state).
- `NOT VALID` means "existing rows unchecked", **not** "not enforced" — new rows are rejected immediately. That asymmetry is what makes the gap between the two migrations safe.
- `SET NOT NULL` skips its verification scan iff a **validated** `CHECK (col IS NOT NULL)` already proves the property (PG 12+). Alone, it full-scans under `ACCESS EXCLUSIVE`.
- DDL waits for its lock **at the head of the queue** — every query arriving behind it also waits. One idle-in-transaction session + a migration = a fully stalled table. Set `lock_timeout` before DDL in production.
- **A column DEFAULT only fires when the column is omitted from the INSERT.** Hibernate includes every insertable mapped column, so it sends an explicit `NULL` and the default never applies — the only way to let it fire is `insertable = false` (which is why `created_at` has it). An unset `@Builder` field is still `NULL`, not absent.
- `@Builder` trades a compile-time completeness check for readability: forget a field and it compiles, then fails at insert against `NOT NULL`. A positional constructor would have refused to compile. Worth it on a wide entity; not free.
- Split migrations **by lock strength and scan cost, not statement count**. Grouping is safe when a file contains only catalog-only ops, or only weak-lock scans — the V4/V5 split was needed because a *strong* lock was taken in statement 1 and held across a *scan* in statement 2.
- **Idempotency is required only where rollback is impossible.** `IF EXISTS`/`IF NOT EXISTS` are mandatory in a non-transactional migration and merely optional in a transactional one, where a failure rolls back and the retry starts clean.
- `@Deprecated` on a field changes nothing at runtime — Hibernate still maps and writes it. A deprecation window is for public APIs with consumers you don't control, not private fields with zero readers.
- **Never hold a DB transaction across a network call you don't control.** `@Transactional` on a batch job that makes HTTP calls sets the transaction's duration by someone else's timeout, and a rollback undoes state for rows whose messages were already sent — turning a retry into a duplicate generator.
- A redundant guard can be worse than useless: `attempts < MAX` alongside a state check is redundant *today*, but **lowering** the config strands rows as `PENDING` forever (skipped by the guard, never marked `FAILED`, `attempts` climbing) — resurrecting the original bug via a config tweak. Let state alone decide.
- **MDC is a `ThreadLocal` per JVM — nothing is shared.** Every thread boundary needs its own copy mechanism, and a boundary separated by *time* (outbox → relay) can only be crossed by persisting the value.
- `@Header(..., required = false)` on a listener is mandatory for optional metadata — Spring's default throws *before* the method body, outside the catch blocks, producing the requeue hot loop those catches exist to prevent.
- Null-guard polarity: `x != null && x.f()` when asking "is it usable?", `x == null || x.f()` when asking "is it unusable?". The null check must be the operand that short-circuits the other away — `!= null || ...` evaluates the right side precisely when the reference is null.
- **Observability must never block a business transaction.** Diagnostic columns stay nullable, and any value accepted from outside must be length-capped at the edge or it becomes a rollback vector.
- In a batch job, the job's MDC describes *the run*; each item carries *its own* id. Conflating them merges unrelated flows into one trace that looks correct.
- MDC exists so you **don't** thread the value through method signatures — a parameter gives it to one method, MDC gives it to every method on the thread.
- `@Transactional` on a batch job that makes network calls is a bug: it sets the transaction's duration by someone else's timeout, and a rollback undoes state for work already delivered.
- `CREATE INDEX CONCURRENTLY` + Flyway **deadlocks by default**: the build waits for all open transactions, and Flyway holds one for its history lock. Needs `spring.flyway.postgresql.transactional-lock: false`. Diagnose with `pg_blocking_pids()` — a hang shows no error and no history row, so it looks like nothing happened.
- **A migration that cannot roll back must be idempotent.** With `executeInTransaction=false` a mid-file failure leaves earlier statements permanently applied and unrecorded, so every statement needs `IF EXISTS` / `IF NOT EXISTS` to survive the retry.
- Keyword order is `CREATE INDEX CONCURRENTLY IF NOT EXISTS` — `CONCURRENTLY` first.
- The `.sql.conf` script-config file must reach `target/classes` too; without it Flyway silently wraps the file in a transaction again.
- A **Bitmap** Index Scan does *not* preserve index order, so `ORDER BY` still costs a Sort. Only a plain `Index Scan` gets the ordering for free.
- A `boolean` cannot hold a **terminal-failure** state. "Pending" and "gave up" both read `false`, so a partial index on it can never shed dead rows. Queue-shaped tables need three states, not two.
- Index predicates must be **immutable literals** — they cannot read config. Baking `attempts < 5` into an index couples schema to `application.yml`, and the failure is asymmetric: *lowering* the config keeps the index usable, *raising* it silently makes it unusable (the query no longer implies the predicate) and you drop to a seq scan with no error. **Index the row's state, not the policy.**
- Replacing a column (vs adding one) needs a **dual-write** phase — both columns written on every change — so old and new code coexist and rollback stays safe. Flip *writes* first, *reads* a deploy later, drop the old column a deploy after that.
- A dedup ledger row's lifetime is governed by **how long redelivery is possible**, not by whether the work finished. Deleting/archiving a *delivered* row re-opens duplicate sends (the relay's publish-then-mark gap will replay the same `message_id`). Retention must be age-based, never status-based.
- A status flag and its timestamp must be written **together or not at all** — setting the timestamp unconditionally produces states that contradict themselves and cannot be reasoned about later.

---

## 10. Plan change log

| Date | Change |
|---|---|
| 2026-09-03 | **THE BUILD IS DONE — bill → notification verified end to end.** AMQP wiring, relay job, `bill.payment.*` binding, `NotificationMessage` enum. **One bill payment → exactly one customer notification**, carrying the correlation id of the originating HTTP request; **seven log lines across three JVMs, five threads, a database and a broker, from one grep.** Decisions: one exchange with producer-owned routing-key namespaces (exchange-per-producer would force consumers to enumerate exchanges — more coupling, not less); one queue, two bindings (separate queues rejected as pre-solving head-of-line blocking — earned in Phase 7, not assumed). Message text moved to an enum because four routing keys cannot fit a two-way ternary; the lookup **throws** rather than shipping generic text. **NEXT ACTION → Phase 7 sabotage**, with three scenarios already collected. |
| 2026-08-31 | **Bill outbox writes.** `V3` table + `V4` cif column; events written transactionally on every resolved outcome; `Failed` writes none (needs manual intervention — a customer cannot act on "we do not know where your money is"). Transaction boundary decided: HTTP **outside**, writes **inside**, on a separate bean because `this.method()` bypasses the proxy. Found a pre-existing cross-service DTO mismatch (`original_entry_id` vs `originalEntryId`) that made every reverse 404 and strand bills — the class of defect mocked tests cannot catch. **Relay next; the bill service has no AMQP wiring yet.** |
| 2026-08-28 | **Notification ownership decided.** Wallet suppresses the whole hold lifecycle via `TransactionType.isCustomerFacing()`. Rule: *the service that knows why the money moved owns the message.* A bill payment now produces **zero** wallet events, so the customer gets **one** message, from the bill service. |
| 2026-08-27 | **Bill service speaks hold/settle**, and no longer knows the wallet's account numbering. A 409 now carries the discharge type so the bill reconciles in the same call — `SETTLEMENT → Paid`, `RELEASE → Rejected`. Deferring that conflict to the sweep would have looped forever. New `Failed` status: `Rejected` implies the customer was refunded, which a refused settle does not. |
| 2026-08-24 | **Ledger transaction types complete** (V13–V19). Seven money-movement kinds, `NOT NULL`, assigned server-side by endpoint. Two discharge invariants — `ck_one_discharge_kind` (a row is a reversal XOR a settlement) and `uq_entry_discharged_once` (a hold is discharged once, either way). The first exists because the learner found the `coalesce` index defeated by a row setting both columns. `settles_entry_id` added after the learner asked whether `entry_id`/`reverses_entry_id` already covered it — they did not, and settlements linked to nothing. |
| 2026-08-15 | **Traceability complete (Bucket D)** — correlation ids now span inbound/outbound HTTP, `@Async`, `@Scheduled`, the outbox (V12/V14) and AMQP, across all three services. Landed ahead of Phase 7 as planned. Key lessons: MDC is per-thread so every boundary needs its own copy mechanism and a *time* gap can only be crossed by persistence; run-id vs item-id must never be conflated; diagnostics fail open (nullable columns + a length cap at the edge, or a header rolls back a transfer); and the plumbing is worthless without log lines — a grep returned one unrelated warning until three boundary `INFO`s were added. Verified: one transfer → five lines, two JVMs, four threads, one broker, one grep. NEXT ACTION → bill events, history #4, or Phase 7. |
| 2026-08-11 | **Service #3 complete.** Retry job (`ResendingJob`) built and verified. **Volume test settled the open index question**: at 500k rows / 50 pending, the partial index is 16 kB and serves the live job's bind-parameter query (0.024 ms vs 55 ms seq scan); a *forced* generic plan does fall back to a 119 ms seq scan, so the protection is the cost gap, not a guarantee. Key insight: the index's biggest win is the **idle** poll, not the busy one — `fixedDelay` runs forever whether or not there is work. Retry maths validated (0.3 failure rate × 5 attempts → predicted 0.24 permanent failures, observed exactly 1). Then **V10–V13 finished the schema**: state columns `NOT NULL`, dual-writing stopped, entity fields removed, booleans and three scaffolding CHECKs dropped. Verified end to end after the drop. Decision: `last_attempt_at` stays audit-only, **no backoff**. NEXT ACTION → pick between bill-service events, correlation IDs, or history #4. |
| 2026-08-10 | **Terminal state added** (V7 `varchar`+`CHECK`, V8 three-branch `CASE` backfill, dual-write in `deliver()`). Root cause named: a boolean cannot distinguish *pending* from *gave up*, so the partial index could never shed dead rows and `attempts` incremented forever. Rejected along the way: a native PG enum (breaks Hibernate varchar binding — reproduced), `attempts` in the index predicate (couples schema to config, fails asymmetrically and silently), and archiving delivered rows (re-opens duplicate sends — the dedup ledger's lifetime is a *time* question, not a status one). Contract steps V9–V11 + index switch still owed. |
| 2026-08-09 | **Notification chain verified end to end**, and `routing_key` added to `processed_events` via the full **expand-contract** dance (V2 nullable → code populates → V3 idempotent backfill to `'unknown'` → V4 `NOT VALID` / V5 `VALIDATE` / V6 `SET NOT NULL`, one statement per file so Flyway's per-file transaction cannot hold `ACCESS EXCLUSIVE` across the scan). Two bugs found by reading data rather than code: `sms_sent_at` left null on success, then set unconditionally producing contradictory rows. NEXT ACTION → the **retry job**. |
| 2026-07-30 | Plan created. Bill-payment phase complete and re-verified; merge to `main` pending. |
| 2026-08-06 | **Notification service #3 built** (`b4a0c4c`): schema, entities, queue+binding, provider client and the consumer — dedup on AMQP message_id, status read from the provider's answer, never throws. Plus a **provider simulator** on 9092 with a 0.3 failure rate so the retry design has something real to react to. Not yet run end to end; **no retry job yet**, so failed sends currently sit unretried. |
| 2026-08-04 | **Relay job done** (`c6c8af6`): publishes outbox rows to the `quickpay.events` topic exchange with dotted routing keys and the outbox id as AMQP `message_id`; marks `sent_at` after a successful publish. Exchange renamed from the misleading `notification-queue`. Verified on the broker. |
| 2026-08-03 | **Outbox write done** (`5ffe478`, branch `feat/notifications`): RabbitMQ scaffolded, transactional outbox decided and built (V11 + entity + write inside `transfer`'s transaction), partial index chosen from measurements. Atomicity verified. Relay job next. |
| 2026-08-01 | `test/bill-service` merged to `main` via PR #4; full suite green on main (wallet 8/8, bill 7/7). |
| 2026-08-01 | **RabbitMQ decision RESOLVED** (ADR-0005, docs branch): broker for event fan-out; bill's `@Async` biller trigger stays (point-to-point to an external system ≠ fan-out). Sponsor decisions log opened with its first three entries. ⚠️ ADR-0005 leaves **reliable publishing** open — outbox pattern, now Step 0 of the notifications build. NEXT ACTION → notifications (#3). |
| 2026-08-01 | **Service decomposition DECIDED** (§5 #2): notifications = #3 (req 5 is itself a decomposition instruction), history = #4 as a read model (a statement needs bill context, so it is a join across two owners; also keeps heavy reads off the money core). **Budget now full.** **AI feature raised and DEFERRED** as a candidate (§5 #5) — not in the brief, blocked behind history existing, read-side so it belongs inside #4 and never in the money path. |
| 2026-08-01 | **Bill-service tests closed** — 7 green (`8d1616b`); #6 and #8 deliberately skipped (manually-verified behaviour, nil marginal learning). Test #7 found three real defects and drove fixes: no re-entry guard in `resolve`, 409 mistreated as failure, no batch isolation in the sweep. NEXT ACTION moved to the decision session. |
| 2026-07-30 | Cross-cutting decisions recorded (Bucket D): **auth deferred** (additive, low learning value, adds friction to every test) but **traceability/correlation-ids moved ahead of the sabotage pass** (pervasive, expensive to retrofit, and Phase 7 is unreadable without it). Added the wallet baseline note — money model settled, service still reopened by notifications, load test, sabotage and auth. |
| 2026-07-30 | `feat/biller-simulator` merged to `main` via PR #3 — bill-service, wallet V7–V10 and both simulators are now on `main`. NEXT ACTION moved to bill-service automated tests (six tests, one at a time). |
| 2026-07-30 | **rev 2 — senior plan review absorbed.** Tests extended 6→8 (#6 UNKNOWN reaction, #7 double-resolve guard + V9 rider; sweep moved last; prediction placeholder added). **Decision session** created in Bucket A (RabbitMQ divergence + service budget) and gated ahead of notifications. **DEFINITION OF DONE** added (draft, pending learner's wording). `/bill/reserve` rename promoted to Immediate. Phase 8 now requires a written breaking-TPS + bottleneck prediction before k6 runs. |