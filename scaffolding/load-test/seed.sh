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
for i in $(seq 1 "$COUNT"); do
  # cif must be exactly 10 chars (@Size(min=10,max=10)); 88 prefix keeps the load-test
  # pool visually distinct from hand-made wallets in the database.
  cif=$(printf "88%08d" "$i")
  body=$(curl -s -X POST "$BASE/v1/wallets" \
      -H 'Content-Type: application/json' \
      -d "{\"cif\":\"$cif\",\"walletName\":\"LOAD-$i\"}")
  wn=$(printf '%s' "$body" | sed -n 's/.*"walletNumber":"\([^"]*\)".*/\1/p')
  if [ -z "$wn" ]; then
    echo "ERROR: wallet creation failed at #$i: $body" >&2
    exit 1
  fi
  created+=("$wn")
  [ $((i % 25)) -eq 0 ] && echo "  created $i/$COUNT"
done

# Activate. A wallet must be Active before transfer() will touch it (WalletNotActiveException).
# Done in SQL because WalletController's activate endpoint declares {walletNumber} in the URI
# template but binds @PathVariable String wallet_number — the names do not match. Seeding must
# not depend on an endpoint whose behaviour is in question; that is a separate finding.
echo "activating..."
docker exec quickpay-wallet-db psql -U wallet -d wallet -q -c \
  "update wallet set status='Active' where cif like '88%' and status <> 'Active';"

# Fund through the REAL top-up endpoint, never by UPDATEing balances directly. A direct
# balance write would create money with no ledger row, drift every wallet, and poison the
# golden-rule check that every Phase 7 scenario depends on.
echo "funding $FUND each..."
n=0
for wn in "${created[@]}"; do
  n=$((n + 1))
  curl -s -o /dev/null -X POST "$BASE/v1/transfer/top-up" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: seed-$(date +%s)-$wn" \
    -d "{\"walletNumber\":\"$wn\",\"amount\":$FUND}"
  [ $((n % 25)) -eq 0 ] && echo "  funded $n/$COUNT"
done

printf '[\n' > "$OUT"
printf '  "%s",\n' "${created[@]}" | sed '$ s/,$//' >> "$OUT"
printf ']\n' >> "$OUT"

echo "wrote $OUT with ${#created[@]} wallets"
echo
echo "golden rule after seeding (must still balance):"
docker exec quickpay-wallet-db psql -U wallet -d wallet -At -c \
  "select '  drifted wallets = '||count(*) from wallet w where w.balance <> coalesce((select sum(debited_amount) from ledger where debited_wallet_number=w.wallet_number),0)+coalesce((select sum(credited_amount) from ledger where credited_wallet_number=w.wallet_number),0);"
