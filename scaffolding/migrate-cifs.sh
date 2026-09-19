#!/usr/bin/env bash
# Migrate non-conforming wallet cifs onto real customer-service cifs.
#
# WHY THIS SHAPE. The wallet's cif column accepts any 10 chars; customer-service
# issues cifs from a sequence under ck_cif_format (^0[0-9]{9}$). You cannot create
# a customer with a chosen cif, so the migration can only run in this direction:
# register a customer, take the cif the sequence gives, rewrite the wallet.
#
# SAFETY. The old->new mapping is written to disk and verified BEFORE any UPDATE
# runs. Without that file the UPDATE is irreversible — nothing else in the system
# records which wallet used to belong to which cif (the ledger keys on
# wallet_number, never cif).
#
# Usage:
#   ./migrate-cifs.sh plan   [legacy|all]   # register + activate + write mapping. NO writes to wallet.
#   ./migrate-cifs.sh apply                 # read the mapping, UPDATE wallet, verify
set -euo pipefail

BASE="${BASE:-http://localhost:8083}"
SCOPE="${2:-legacy}"
MAP="$(dirname "$0")/cif-mapping.tsv"
NID_BLOCK="${NID_BLOCK:-19501}"     # national ids 1950100001+ — must not collide with existing

psqlw() { docker exec quickpay-wallet-db psql -U wallet -d wallet -At -c "$1"; }

case "$SCOPE" in
  legacy) FILTER="and cif not like '88%'" ;;
  all)    FILTER="" ;;
  *) echo "scope must be legacy|all" >&2; exit 1 ;;
esac

plan() {
  echo "scope=$SCOPE  base=$BASE"

  # Pre-flight. Without this, `set -e` turns a stopped service into a bare
  # "exit 7" partway through the loop — after some customers already exist.
  # Fail before doing any work, not during it.
  if ! curl -s -o /dev/null --max-time 3 "$BASE/v1/login" -X POST; then
    echo "ERROR: customer-service is not answering at $BASE" >&2
    echo "  Start it, then re-run. (apply needs only the database, not this service.)" >&2
    exit 1
  fi
  # bash 3.2 on macOS has no `mapfile`; read into an array the portable way.
  CIFS=()
  while IFS= read -r line; do
    [ -n "$line" ] && CIFS+=("$line")
  done < <(psqlw "select distinct cif from wallet
                  where not is_internal and cif !~ '^0[0-9]{9}\$' $FILTER order by cif;")
  echo "non-conforming cifs to migrate: ${#CIFS[@]}"
  [ "${#CIFS[@]}" -eq 0 ] && { echo "nothing to do"; exit 0; }

  : > "$MAP"
  n=0
  for old in "${CIFS[@]}"; do
    n=$((n+1))
    nid=$(printf "%s%05d" "$NID_BLOCK" "$n")          # 10 digits, starts with 1 -> ck_national_id_format
    email="migrated-${old}@quickpay.test"
    phone=$(printf "05%08d" "$n")
    # `|| true` so a transport failure reaches the check below with a readable
    # message, instead of set -e killing the run with a bare curl exit code.
    body=$(curl -s -X POST "$BASE/v1/customer" -H 'Content-Type: application/json' \
      -d "{\"nationalId\":\"$nid\",\"customerName\":\"Migrated $old\",\"phoneNumber\":\"$phone\",\"email\":\"$email\",\"password\":\"Migrated!2026\"}" || true)
    new=$(printf '%s' "$body" | sed -n 's/.*"cif":"\([^"]*\)".*/\1/p')
    if [ -z "$new" ]; then
      echo "FAILED to register a customer for $old" >&2
      echo "  response: ${body:-<none — service unreachable>}" >&2
      echo "  $n customer(s) already created; the mapping file has $(wc -l < "$MAP" | tr -d ' ') row(s)." >&2
      echo "  wallet-db is UNTOUCHED. Re-running plan will create duplicates — clean up first." >&2
      exit 1
    fi

    # Activate: a PENDING customer cannot log in, so an unactivated owner could
    # never exercise the ownership check the migration exists to enable.
    curl -s -o /dev/null -X PUT "$BASE/v1/customer/activate" -H 'Content-Type: application/json' \
      -d "{\"cif\":\"$new\",\"password\":\"Migrated!2026\"}"

    printf '%s\t%s\n' "$old" "$new" >> "$MAP"
    echo "  $old -> $new"
  done

  echo
  echo "mapping written: $MAP ($(wc -l < "$MAP") rows)"
  echo "NOTHING in wallet-db has changed yet. Review the file, then: $0 apply"
}

apply() {
  [ -s "$MAP" ] || { echo "no mapping file at $MAP — run plan first" >&2; exit 1; }
  echo "before:"
  psqlw "select '  non-conforming wallets = '||count(*) from wallet where not is_internal and cif !~ '^0[0-9]{9}\$';"

  # One statement per pair, all inside a single transaction: either every wallet
  # moves or none does. A half-applied migration is the worst outcome — some
  # wallets owned, some orphaned, and no way to tell which without the mapping.
  { echo "begin;"
    while IFS=$'\t' read -r old new; do
      echo "update wallet set cif='$new' where cif='$old';"
    done < "$MAP"
    echo "commit;"
  } | docker exec -i quickpay-wallet-db psql -U wallet -d wallet -q

  echo "after:"
  psqlw "select '  non-conforming wallets = '||count(*) from wallet where not is_internal and cif !~ '^0[0-9]{9}\$';"
  psqlw "select '  wallets now owned by a real cif = '||count(*) from wallet where not is_internal and cif ~ '^0[0-9]{9}\$';"

  echo "golden rule (must be 0 drifted, and balances must still sum to 0):"
  psqlw "select '  drifted = '||count(*) from wallet w where w.balance <> coalesce((select sum(debited_amount) from ledger where debited_wallet_number=w.wallet_number),0)+coalesce((select sum(credited_amount) from ledger where credited_wallet_number=w.wallet_number),0);"
  psqlw "select '  sum(balance) = '||sum(balance) from wallet;"
}

case "${1:-}" in
  plan)  plan ;;
  apply) apply ;;
  *) echo "usage: $0 plan [legacy|all] | $0 apply" >&2; exit 1 ;;
esac
