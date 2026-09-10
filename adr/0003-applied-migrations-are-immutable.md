# ADR-0003 — Applied Flyway migrations are immutable (forward-only)

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** Abdulaziz

## Context
Flyway records every migration it applies in `flyway_schema_history`, storing
the version, the script name, and a **checksum** of the script's contents. On
each startup it validates that what's on disk still matches what was applied.
While reworking the schema, a migration that had **already been applied to dev**
was renumbered (V5 → V6). On the next startup Flyway would have seen a version /
checksum mismatch against history and failed validation — breaking the
environment. The rename was caught and reverted before damage, but the rule it
violated needs to be explicit so it doesn't recur in dev, CI, or prod.

## Options considered
1. **Edit / renumber migrations freely** while iterating. Convenient locally,
   but any environment that already ran the old version breaks on the next
   startup (checksum/version mismatch), and the history stops being a true
   record of what happened.
2. **Treat applied migrations as immutable; forward-only.** Once a migration
   has run anywhere, it is frozen; every change is a new, higher-versioned
   migration.

## Decision
Applied migrations are immutable. Never edit, rename, renumber, or reorder a
migration that has been applied to any environment. Fix mistakes by adding a
new corrective migration with the next version number.

## Consequences
- ✅ dev / CI / prod never break on a checksum or version mismatch.
- ✅ The migration history stays an accurate, append-only audit log of how the
  schema actually evolved — same discipline as the append-only ledger.
- ❌ A mistake in an applied migration costs an extra corrective migration file
  rather than a quick edit; migrations can never be reordered after the fact.
- 🔁 Editing an unapplied, not-yet-committed migration is fine — the rule binds
  only once a migration has run somewhere.