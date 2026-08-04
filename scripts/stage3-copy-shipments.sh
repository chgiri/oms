#!/usr/bin/env bash
set -euo pipefail

# STATUS: EXECUTED. This has already been run successfully as part of
# Shipment's Stage 3 cutover, which is complete — Stage 5 has since deleted
# the shipment package from oms-main entirely (including
# app.shipment.writes-frozen and OrderConfirmedShipmentConsumer, both
# referenced below as they existed AT THE TIME this ran) and dropped
# oms_shipment itself (V22__drop_oms_shipment_schema.sql). This script can
# no longer be run again against oms-main in its current state — the source
# schema/table/module it reads from don't exist anymore. Kept as a historical
# record of the exact procedure that was followed, and as the template
# Product/Customer's own copy scripts are expected to follow when their turn
# comes.
#
# Stage 3 of the microservices-prep plan (Phase 4, Shipment extraction):
# one-time copy of oms_shipment.shipments (oms-main) into shipments
# (shipment-service's own database). See
# docs/stage3-data-cutover-runbook-shipment.md for the full maintenance-window
# procedure this script is one step of — do not run this on its own without
# following that runbook, in particular:
#   (a) app.shipment.writes-frozen=true has taken effect on EVERY running
#       oms-main instance, AND
#   (b) oms-main's OrderConfirmedShipmentConsumer has been fully stopped
#       (not just gated) before this runs — (a) alone does not stop it,
#       since the freeze flag is defense-in-depth for that consumer, not its
#       primary guard. See that consumer's Javadoc.
#
# Requires: psql on PATH, network access to both databases from wherever
# this runs, and a role on each side with SELECT (source) / INSERT (target)
# on the shipments table.
#
# NOT idempotent by design — see the pre-flight check below. Re-running
# this against a target that already has rows is refused rather than
# silently double-inserting.

SOURCE_DSN="${SOURCE_DSN:?Set SOURCE_DSN, e.g. postgresql://user:pass@oms-db-host:5432/oms}"
TARGET_DSN="${TARGET_DSN:?Set TARGET_DSN, e.g. postgresql://user:pass@shipment-db-host:5432/shipment_service}"

# Column list is explicit and shared between the SELECT and the \copy target
# column list — physical column order matches on both sides here (shipment-service's
# fresh V1 migration was seeded from this exact current-state shape, see that
# migration's own comment), but naming columns explicitly is cheap insurance
# against that ever drifting.
COLUMNS="id, order_id, carrier, tracking_number, status, shipped_at, delivered_at, version, created_at, updated_at"

# Row-level checksum: casting the whole row to text and hashing it avoids
# the false-collision risk of naively concatenating column values with no
# delimiter. Postgres's row-to-text cast handles NULLs and types consistently
# on both sides, since both databases have identical column types for every
# column in $COLUMNS.
checksum_query() {
  local table="$1"
  echo "SELECT md5(string_agg(md5((id, order_id, carrier, tracking_number, status, shipped_at, delivered_at, version, created_at, updated_at)::text), '' ORDER BY id)) FROM ${table};"
}

echo "== Stage 3 data cutover: oms_shipment.shipments -> shipment-service's shipments =="
echo

# --- Pre-flight: refuse to run against a non-empty target -----------------
target_count=$(psql "$TARGET_DSN" -tAc "SELECT COUNT(*) FROM shipments;")
if [ "$target_count" -ne 0 ]; then
  echo "ABORT: target 'shipments' table is not empty ($target_count rows)." >&2
  echo "This script is not idempotent — re-running it against a non-empty" >&2
  echo "target would duplicate every row. If this is a genuine retry after" >&2
  echo "a failed attempt, TRUNCATE shipment-service's shipments table first" >&2
  echo "(and RESTART IDENTITY to reset the sequence) — see the runbook's" >&2
  echo "rollback section before doing that." >&2
  exit 1
fi

# --- Snapshot the source for later verification ----------------------------
source_count=$(psql "$SOURCE_DSN" -tAc "SELECT COUNT(*) FROM oms_shipment.shipments;")
source_max_id=$(psql "$SOURCE_DSN" -tAc "SELECT COALESCE(MAX(id), 0) FROM oms_shipment.shipments;")
source_checksum=$(psql "$SOURCE_DSN" -tAc "$(checksum_query oms_shipment.shipments)")

echo "Source: $source_count rows, max id $source_max_id"
echo "Source checksum: $source_checksum"
echo

if [ "$source_count" -eq 0 ]; then
  echo "NOTE: source table is empty. Unlike Product/Customer, this may be" >&2
  echo "expected for Shipment depending on how much order volume has gone" >&2
  echo "through the confirmed stage — confirm this is really the case" >&2
  echo "(not the wrong SOURCE_DSN, and not that shipments haven't been" >&2
  echo "migrated to oms_shipment yet — see V20) before treating an empty" >&2
  echo "copy as normal and proceeding to unfreeze/cutover anyway." >&2
  exit 1
fi

# --- Copy --------------------------------------------------------------------
echo "Copying $source_count rows..."
psql "$SOURCE_DSN" -c "\copy (SELECT $COLUMNS FROM oms_shipment.shipments ORDER BY id) TO STDOUT" \
  | psql "$TARGET_DSN" -c "\copy shipments ($COLUMNS) FROM STDIN"

# --- Advance the target's identity sequence ---------------------------------
# shipments.id is GENERATED BY DEFAULT AS IDENTITY on both sides, which is
# exactly what allows explicit ids in the \copy above — but the target's
# sequence has no idea those ids were just used. Without this, the next
# INSERT with no explicit id would try id 1 again and hit the primary key.
psql "$TARGET_DSN" -c "SELECT setval(pg_get_serial_sequence('shipments', 'id'), $source_max_id);" > /dev/null
echo "Target identity sequence advanced to $source_max_id."
echo

# --- Verify ------------------------------------------------------------------
target_count=$(psql "$TARGET_DSN" -tAc "SELECT COUNT(*) FROM shipments;")
target_max_id=$(psql "$TARGET_DSN" -tAc "SELECT COALESCE(MAX(id), 0) FROM shipments;")
target_checksum=$(psql "$TARGET_DSN" -tAc "$(checksum_query shipments)")

echo "Target: $target_count rows, max id $target_max_id"
echo "Target checksum: $target_checksum"
echo

if [ "$target_count" != "$source_count" ] || [ "$target_checksum" != "$source_checksum" ]; then
  echo "VERIFICATION FAILED — target does not match source." >&2
  echo "  row count:  source=$source_count target=$target_count" >&2
  echo "  checksum:   source=$source_checksum target=$target_checksum" >&2
  echo "DO NOT proceed to unfreezing writes or restarting shipment-service's consumer." >&2
  echo "See the runbook's rollback section." >&2
  exit 1
fi

echo "OK — $target_count rows copied and verified (checksum match, sequence advanced)."
echo "Next: the runbook's post-copy verification queries, then starting"
echo "shipment-service's own OrderConfirmedShipmentConsumer (Stage 4/5)."
