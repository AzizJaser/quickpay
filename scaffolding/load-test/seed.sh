#!/usr/bin/env bash
# Phase 8 seed — build a pool of funded customer wallets for the load test.
#
# WHY THIS EXISTS. Three ways a load test lies about its own results, all avoided here:
#
#  1. BALANCE DEPLETION. Hammer one sender for 60s and it runs dry; InsufficientBalance
#     then arrives as a 400 and looks exactly like a breaking point. Every wallet is
#     funded far beyond what the run can spend.
#  2. ACCIDENTAL CONTENTION. A small pool means random pairs collide on the same rows,
#     so you measure lock contention you invented rather than the service's limit.
#     Default 200 wallets => 39,800 ordered pairs.
#  3. WARM-UP. The first requests hit a cold JIT, an empty connection pool and unplanned
#     query plans. Seeding through the same endpoints warms all three.
#
# Usage:  ./seed.sh [WALLET_COUNT] [BASE_URL]
set -euo pipefail

COUNT="${1:-200}"
BASE="${2:-http://localhost:8080}"
OUT="$(dirname "$0")/wallets.json"
FUND=100000000          # 100m per wallet — a 10-minute run at 2000 TPS spends 1.2m

echo "seeding $COUNT wallets against $BASE"

# Pre-flight: fail loudly here rather than three minutes into a k6 run.
if ! curl -sf --max-time 3 "$BASE/actuator/health" >/dev/null 2>&1; then
  if ! curl -s --max-time 3 -o /dev/null "$BASE/v1/wallets/000000000001"; then
    echo "ERROR: $BASE is not answering. Start the stack first:" >&2
    echo "  docker compose -f docker-compose.yml -f docker-compose.load.yml up -d --build" >&2
    exit 1
  fi
fi

created=()
cifs=()
for i in $(seq 1 "$COUNT"); do
  # cif must match ^0[0-9]{9}$ (CreateWalletRequest @Pattern, added with increment 5A) —
  # a cif STARTS WITH 0. The old "88" prefix violated that and now 400s. "08" keeps the
  # load-test pool visually distinct from hand-made wallets while conforming: 0800000001…
  # (The wallet table has no FK to customers, so a load cif needs no matching customer row.)
  cif=$(printf "08%08d" "$i")
  body=$(curl -s -X POST "$BASE/v1/wallets" \
      -H 'Content-Type: application/json' \
      -d "{\"cif\":\"$cif\",\"walletName\":\"LOAD-$i\"}")
  wn=$(printf '%s' "$body" | sed -n 's/.*"walletNumber":"\([^"]*\)".*/\1/p')
  if [ -z "$wn" ]; then
    echo "ERROR: wallet creation failed at #$i: $body" >&2
    exit 1
  fi
  created+=("$wn")
  cifs+=("$cif")      # parallel to created[] — the funding loop needs each wallet's owner
  [ $((i % 25)) -eq 0 ] && echo "  created $i/$COUNT"
done

# Activate. A wallet must be Active before transfer() will touch it (WalletNotActiveException).
# Done in SQL because WalletController's activate endpoint declares {walletNumber} in the URI
# template but binds @PathVariable String wallet_number — the names do not match. Seeding must
# not depend on an endpoint whose behaviour is in question; that is a separate finding.
echo "activating..."
docker exec quickpay-wallet-db psql -U wallet -d wallet -q -c \
  "update wallet set status='Active' where cif like '08%' and status <> 'Active';"

# Fund through the REAL top-up endpoint, never by UPDATEing balances directly. A direct
# balance write would create money with no ledger row, drift every wallet, and poison the
# golden-rule check that every Phase 7 scenario depends on.
#
# top-up now carries a cif (increment 5A): TopUpRequest requires it, and the wallet asserts
# the cif owns the CREDITED (topped-up) wallet. So each call must send that wallet's own cif —
# cifs[] is kept parallel to created[] for exactly this.
echo "funding $FUND each..."
n=0
for idx in "${!created[@]}"; do
  wn="${created[$idx]}"
  cif="${cifs[$idx]}"
  n=$((n + 1))
  curl -s -o /dev/null -X POST "$BASE/v1/transfer/top-up" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: seed-$(date +%s)-$wn" \
    -d "{\"cif\":\"$cif\",\"walletNumber\":\"$wn\",\"amount\":$FUND}"
  [ $((n % 25)) -eq 0 ] && echo "  funded $n/$COUNT"
done

# wallets.json now carries {walletNumber, cif} pairs, not bare numbers: since increment 5A,
# a transfer must assert the debited wallet's owner, so the k6 scripts need each wallet's cif.
printf '[\n' > "$OUT"
for idx in "${!created[@]}"; do
  sep=","; [ "$idx" -eq $(( ${#created[@]} - 1 )) ] && sep=""
  printf '  {"walletNumber":"%s","cif":"%s"}%s\n' "${created[$idx]}" "${cifs[$idx]}" "$sep" >> "$OUT"
done
printf ']\n' >> "$OUT"

echo "wrote $OUT with ${#created[@]} wallets"
echo
echo "golden rule after seeding (must still balance):"
docker exec quickpay-wallet-db psql -U wallet -d wallet -At -c \
  "select '  drifted wallets = '||count(*) from wallet w where w.balance <> coalesce((select sum(debited_amount) from ledger where debited_wallet_number=w.wallet_number),0)+coalesce((select sum(credited_amount) from ledger where credited_wallet_number=w.wallet_number),0);"
