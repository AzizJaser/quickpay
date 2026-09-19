# cif backfill — giving pre-auth wallets real owners (19 Sep 2026)

- **Status:** ✅ applied to the local dev `wallet-db`
- **Mapping:** [`cif-backfill-2026-09-19.tsv`](cif-backfill-2026-09-19.tsv) — 23 rows
- **Unblocks:** increment 5, the wallet-side ownership check (ADR-0006)

> ⚠️ **This is NOT a schema change and there is deliberately no Flyway migration.**
> The affected wallets were created through the API during Phases 5–8, not by any `V*.sql`.
> A fresh environment replays V1–V20 and gets **none of them** — only the four internal
> accounts, whose cif `0000000000` already conforms. Nothing to migrate there.

## Why this was needed

Increment 5 makes the wallet verify that an asserted cif owns the wallet being touched.
Measured 19 Sep, before any of it was built:

```
wallet cifs in the database:  5100000001, 6600000003, 8800000042, 9911111111, …
customer-service issues:      ^0[0-9]{9}$   (ck_cif_format, from seq_customers_cif)
customer wallets conforming:  1 of 232
```

**The two services' identifier populations were completely disjoint.** A correctly built
ownership check would have rejected all 231 wallets — not because the check was wrong, but
because none of those wallets had an owner that could exist.

## Why the obvious fix was impossible

"Create customers for the existing cifs" cannot be done, by two independent constraints:

1. **`ck_cif_format` rejects them at INSERT.** `5100000001` starts with `5`. The CHECK
   applies to direct SQL, not just the API.
2. **The cif is never supplied.** `CustomerRequest` has no cif field; registration takes the
   next value from `seq_customers_cif`.

That format rule is load-bearing — it is what keeps cifs and national IDs **format-disjoint**
(`^0…` vs `^[12]…`), which is how the identifier collision was solved without resizing
columns across 970,358 ledger rows. Relaxing it to adopt test fixtures would have spent a
production invariant to keep dev data alive.

So the migration could only run in the **opposite** direction: register a customer, take the
cif the sequence issues, rewrite the wallet.

## What made it cheap

```
cif appears in exactly ONE table: wallet
```

The ledger keys on `wallet_number` and never stores a cif. So rewriting ownership is a
31-row `UPDATE` with **zero ledger impact** — including `5100000001`, which carries 40,524
ledger rows that did not need to be touched or even read.

## Scope — migrate the legacy fixtures, regenerate the load pool

| cohort | cifs | wallets | decision |
|---|---|---|---|
| legacy scenario wallets (`5x`–`9x`) | 23 | 31 | ✅ **migrated** — they carry Phase 5–7 history |
| Phase 8 load pool (`88…`) | 200 | 200 | ⬜ **left non-conforming** — `seed.sh` recreates them from nothing; fabricating 200 customers for wallets that get discarded each run buys nothing |
| internal (`0000000000`) | 1 | 4 | n/a — already conforms |

⚠️ **Consequence: 200 non-conforming wallets remain.** They are unreachable through the BFF
by construction (no customer can assert an `88…` cif) and that is correct — they have no
owner. They stop existing the next time the load pool is regenerated.

## Method

`scratchpad/migrate-cifs.sh`, two phases, deliberately separated:

**`plan`** registers and activates one customer per old cif, then writes the mapping to disk.
**Nothing in `wallet-db` is touched.** Activation matters: a PENDING customer cannot log in,
so an unactivated owner could never exercise the check this migration exists to enable.

**`apply`** reads the mapping and runs every `UPDATE` inside **one transaction**. A
half-applied migration is the worst outcome — some wallets owned, some orphaned, and no way
to tell which without the mapping file.

⚠️ **The mapping file is the only record of the old values.** Neither database retains them.
That is why it lives in this directory rather than in a scratch folder.

## Verification — predictions written before the run, all three correct

| | predicted | actual |
|---|---|---|
| mapping rows and cif range | 23, `0000000012`–`0000000034` | **exact** ✅ |
| `sum(balance)` across all wallets | 0 | **0** ✅ |
| drifted wallets | none | **0** ✅ |

```
non-conforming wallets   231 → 200
wallets with a real cif    1 →  32
```

**The golden rule held across an ownership rewrite on 31 live wallets.**

End to end through the BFF, four spot checks, each logging in as the migrated customer:

| was | now | wallets | balance | expected |
|---|---|---|---|---|
| `5100000001` | `0000000012` | 5 | 32,412 | 32,412 ✅ |
| `5100000002` | `0000000013` | 5 | 6,369 | 6,369 ✅ |
| `6600000001` | `0000000018` | 1 | 3,000 | 3,000 ✅ |
| `9911111111` | `0000000034` | 1 | 0 | 0 ✅ |

Each customer sees **only** their own wallets — the mapping is 1:1, not collapsed.

## ⚠️ Still open — this fixed the data, not the cause

**`CreateWalletRequest` validates length but not format:**

```java
@NotBlank
@Size(min = 10, max = 10)   // no @Pattern
String cif
```

The wallet accepts any ten characters as a cif. That is how all 223 non-conforming rows got
there, and nothing prevents number 224 tomorrow. **The `^0[0-9]{9}$` rule belongs here too**,
so the two services cannot disagree about what a cif is.

When it lands, **`seed.sh` breaks** — it generates `88%08d` cifs. That is scaffolding: it
should register customers through the API and create conforming wallets, making the load pool
consistent by construction rather than by cleanup.

## Accepted, on the record

The 23 customers are **fabricated** — synthetic national IDs (`19501…`), emails
(`migrated-<old-cif>@quickpay.test`) and phone numbers, all sharing one password. That was a
deliberate call: these are test wallets in a learning system, and giving them owners is no
less real than the wallets themselves. **In a system with real customers this migration would
be unacceptable** — you cannot invent account holders — and the answer there would have been
to orphan the wallets and reconcile them by hand.