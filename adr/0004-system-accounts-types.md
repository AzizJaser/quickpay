# ADR-0004 — Split `is_system` into `is_internal` and `allows_negative`

- **Status:** Proposed
- **Date:** 2026-06-21
- **Deciders:** Abdulaziz
- **Relates to:** Refines ADR-0002 (system accounts may go negative) — does **not** supersede it.

## Context
Introducing the bill service requires a **suspense (temp) account** that holds the amount
reserved from a customer's wallet between *reserve* and *capture/reverse*. It is a pass-through
holding account: money may only leave what entered it, so it must **never go negative**.

The current model cannot express this. ADR-0002 lets system accounts skip the `balance >= 0`
rule via `CHECK (balance >= 0 OR is_system)`, because the inward (money-source) account is
*designed* to go negative. But the suspense account is also a system account and would inherit
that same exemption — letting it go negative, which would let a reverse-after-capture **create
money**. The root cause: the single `is_system` flag secretly encodes **two different concepts** —
"this is an internal account, not a customer wallet" and "this account may go negative" — that
happen to coincide for inward but diverge for the suspense account.

## Options considered
1. **Reuse `is_system` for the suspense account (status quo)** — zero schema change. Rejected:
   `is_system` grants the negative-balance exemption (ADR-0002), so the suspense account could go
   negative → golden-rule breach (money created on reverse-after-capture).
2. **Don't flag the suspense account as `is_system` at all** — it falls under `balance >= 0`, which
   is what we want. Rejected as a half-measure: the account loses other internal-account behavior
   (the transfer balance-check skip, classification/reporting), and `is_system` still conflates the
   two concepts for every other account.
3. **Allowlist the exception accounts** — keep `balance >= 0` for everyone except a named set of
   source-account numbers. Works, but encodes the policy as an external list rather than as an
   honest per-account property; harder to reason about per row.
4. **Split the flag (chosen)** — replace `is_system` with two independent booleans, one per concept.

## Decision
Split `is_system` into two orthogonal flags:
- **`is_internal`** — the account is system-owned, not a customer wallet (inward, outward, suspense).
- **`allows_negative`** — the account is a money source and may carry a negative balance.

Replace the constraint `CHECK (balance >= 0 OR is_system)` with
**`CHECK (balance >= 0 OR allows_negative)`**. Only the inward account sets `allows_negative = true`.

Backfill: inward (`…0001`) → `is_internal=true, allows_negative=true`; outward (`…0002`) →
`is_internal=true, allows_negative=false`; suspense (`…0003`, new) → `is_internal=true,
allows_negative=false`.

**Scope / assumption:** this ADR assumes a **single shared suspense account** (`…0003`) for all
in-flight bill reservations — the per-bill reserved amount is tracked by the ledger and the bill
record, not by a separate account per bill. Whether to keep one shared suspense account vs. one
per bill/attempt is **deferred to the bill-service schema ADR**; the flag model decided here works
either way.

## Consequences
- ✅ Each flag means exactly one thing. The negative-balance exemption shrinks from "all system
  accounts" to "the one true money source" (inward).
- ✅ The suspense account is `is_internal=true, allows_negative=false`, so `balance >= 0` is
  enforced. A reverse firing after a capture (suspense already empty) now **fails at the DB** — a
  golden-rule backstop sitting behind the bill service's state machine.
- ✅ Future internal accounts (settlement, fees, etc.) can be modelled precisely without inheriting
  an unwanted overdraft exemption.
- ❌ Migration cost: add `allows_negative`, rename `is_system → is_internal`, backfill the existing
  accounts; the rename touches the `Wallet` entity and every `is_system` reference in code.
- ❌ **Trap to honor in code:** the transfer balance-check skip must key off **`allows_negative`**,
  NOT `is_internal`. If it keys off `is_internal`, the suspense account would skip the check in
  application logic and overdraw *before* the DB constraint can stop it. The app-layer check and the
  DB `CHECK` must agree on the same flag (`allows_negative`).
- 🔁 Revisit if: a new account type needs a third orthogonal property (reconsider whether these
  booleans should become an `account_type` enum), or if a concrete need for more than one
  money-source account appears.