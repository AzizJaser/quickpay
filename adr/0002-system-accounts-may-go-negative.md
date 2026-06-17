# ADR-0002 — System accounts may go negative

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** Abdulaziz

## Context
Money enters and leaves the closed wallet system through internal "system"
accounts: an inward account that is the source of topped-up money, and an
outward account that is the sink for withdrawals. For reconciliation we want
one uniform invariant to hold for *every* wallet: `balance == SUM(its ledger
legs)`. But the source account, by definition, pays money out before any
matching inflow exists — its balance is naturally negative (its magnitude =
total money injected into the system). The existing `CHECK(balance >= 0)`
guard, which is correct and necessary for customer wallets, would reject this.

## Options considered
1. **Keep `balance >= 0` for all accounts**, and special-case the system
   accounts outside the ledger (e.g. don't store their balance, or seed a huge
   starting balance). Hides the truth and breaks the uniform reconciliation
   invariant.
2. **Lean option — let system accounts go negative.** Tag them and relax the
   balance floor for them only.

## Decision
Add an `is_system` boolean column (NOT NULL DEFAULT false), mark the inward and
outward accounts `is_system = true`, and change the constraint to
`CHECK(balance >= 0 OR is_system)`. Reset the system accounts' balances to the
true SUM of their legs.

## Consequences
- ✅ `balance == SUM(legs)` now holds **uniformly** for every wallet, so the
  reconciliation job needs no special cases — it just looks for drift.
- ✅ The inward account's negative balance is a meaningful number: total money
  ever injected. Outward's positive balance: total ever withdrawn.
- ❌ A system account has no balance floor — nothing structurally stops it
  going arbitrarily negative. These accounts must be operationally trusted and
  monitored; they are not customer-facing.
- 🔁 Revisit if a *customer* account ever legitimately needs to go negative
  (overdraft) — that is a different feature and must NOT reuse `is_system`.