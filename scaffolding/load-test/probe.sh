#!/usr/bin/env bash
# Sample the database WHILE a load test runs.
#
# A breaking TPS with no named bottleneck fails the Phase 8 definition of done. k6 tells
# you the service stopped keeping up; it cannot tell you why. This samples the two places
# the answer lives:
#
#   pg_stat_activity.wait_event_type — what backends are BLOCKED on right now.
#       'Lock'   -> row contention (someone else holds the row you want)
#       'Client' -> idle, waiting on the app  (i.e. Postgres is NOT the constraint)
#       'IO'     -> disk
#   active vs total connections      — if active plateaus at the Hikari maximum while
#                                      offered load keeps climbing, the pool is the cap.
#
# Usage:  ./probe.sh [SECONDS] [INTERVAL]
set -euo pipefail
DURATION="${1:-220}"
INTERVAL="${2:-5}"
END=$(( $(date +%s) + DURATION ))

printf "%-10s %8s %8s %10s  %s\n" "time" "active" "idle" "lock_waits" "top wait events"
while [ "$(date +%s)" -lt "$END" ]; do
  docker exec quickpay-wallet-db psql -U wallet -d wallet -At -F'|' -c "
    select
      (select count(*) from pg_stat_activity where datname='wallet' and state='active'),
      (select count(*) from pg_stat_activity where datname='wallet' and state like 'idle%'),
      (select count(*) from pg_stat_activity where datname='wallet' and wait_event_type='Lock'),
      coalesce((select string_agg(label||' x'||n, ', ') from (
          select coalesce(wait_event_type,'running')||':'||coalesce(wait_event,'-') as label, count(*) as n
          from pg_stat_activity where datname='wallet' and state='active'
          group by 1 order by 2 desc limit 3) t), '-')
  " 2>/dev/null | awk -F'|' -v t="$(date +%T)" '{printf "%-10s %8s %8s %10s  %s\n", t, $1, $2, $3, $4}'
  sleep "$INTERVAL"
done