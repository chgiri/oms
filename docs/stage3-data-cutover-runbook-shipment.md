# Stage 3 data cutover runbook — Shipment

> **STATUS: EXECUTED.** This cutover has been run and confirmed stable —
> shipment-service's database is now the sole source of truth for shipment
> data, and Stage 5 (deleting the `shipment` package and dropping
> `oms_shipment` via `V22__drop_oms_shipment_schema.sql`) has been completed
> in oms-main. This document is kept as a historical record of the procedure
> that was followed, and as the template Product/Customer's own cutovers
> are expected to follow — it is not a pending task.

Phase 4 (Shipment extraction) of the microservices-prep plan. This is the
maintenance-window procedure for migrating `oms_shipment.shipments` from
oms-main into shipment-service's own database.

**Read this in full before starting the window.** Steps are numbered but
several are irreversible-in-practice once done (see each step's "why this
order matters" note) — this is not a checklist to skim.

## How this differs from the Product/Customer runbooks — read this first

Product and Customer only have ONE write path each: a REST endpoint.
Freezing that path with a flag, copying the data, then redeploying with the
call site swapped, is the whole story.

Shipment has a SECOND write path Product/Customer don't: oms-main's
`OrderConfirmedShipmentConsumer` auto-creates a shipment on every
`OrderConfirmed` Kafka event, entirely independent of the REST API. A write
freeze flag doesn't stop a running `@KafkaListener` from continuing to
consume and process messages — it only stops `ShipmentServiceImpl`'s own
methods from doing anything if somehow still called (see
`ShipmentWritesFrozenException`'s Javadoc: the flag check there is
defense-in-depth, not the primary safeguard).

The real hazard: shipment-service's own copy of this consumer uses the
**same Kafka consumer group id** as oms-main's (`oms-shipment-service`) —
deliberately, so the handoff carries over from the last committed offset
with no gap. But that also means **the two must never run concurrently**.
If both are simultaneously connected to that group, Kafka splits the
topic's partitions between them rather than having both process every
message — silently routing some orders' shipments into oms-main's database
and others into shipment-service's, based on partition assignment you don't
control. This is worse than simple duplication: it fails silently, and a
row count on either side alone looks completely normal.

So this runbook has an extra step Product/Customer's don't: **fully
stopping** oms-main's consumer (not just freezing REST writes) before
shipment-service's own consumer is allowed to start.

## Why Stage 4 is simpler here than for Product/Customer

Product/Customer's Stage 4 requires an oms-main deploy — swapping
`OrderServiceImpl`/`InventoryServiceImpl` to call `ProductClient`/
`CustomerClient` instead of an in-process service. **Shipment has no
equivalent oms-main deploy for Stage 4** — nothing in oms-main calls
`ShipmentService` synchronously today (see the Phase 4 planning notes), so
there's no synchronous call site inside oms-main to swap. shipment-service's
own `OrderClient` (calling OUT to oms-main) already exists from Stage 2 and
needs no further change here. What remains for oms-main, once this cutover
is confirmed stable, is purely Stage 5 (deletion) — see that stage.

## Pre-window checklist

- [ ] shipment-service is deployed, migrated (its own `V1`/`V2` — empty
      `shipments` and `outbox_events` tables), and reachable from wherever
      `scripts/stage3-copy-shipments.sh` will run — but **not yet started
      as a running instance** (or started with its Kafka consumer disabled
      via `app.process.role=web`) until Step 4 below.
- [ ] `ORDER_SERVICE_BASE_URL` on shipment-service points at the correct
      oms-main address for this environment.
- [ ] `AUTH_SERVICE_JWKS_URI` on shipment-service points at the correct
      oms-main JWKS endpoint for this environment.
- [ ] A rehearsal of `scripts/stage3-copy-shipments.sh` has been run against
      a staging copy of production-shaped data at least once, successfully.
- [ ] You have a way to confirm oms-main's `oms-shipment-service` consumer
      group has zero active members (e.g. `kafka-consumer-groups.sh
      --describe --group oms-shipment-service`) before Step 4.
- [ ] Everyone who might deploy to oms-main during the window knows not to.

## Step 1 — Freeze Shipment writes in oms-main (REST)

Set `app.shipment.writes-frozen=true` (env var `SHIPMENT_WRITES_FROZEN=true`)
and redeploy **every** running oms-main instance — not a live config
refresh, same reasoning as Product/Customer's runbooks: redeploying is the
only way to be certain every instance actually picked up the new value.

**Confirm the freeze took effect:**

```
curl -X POST https://<oms-main>/api/v1/shipments \
  -H "Authorization: Bearer <any valid token>" \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"carrier":"UPS"}'
```

Expect `503` with `errorCode: ESH501` (`SHIPMENT_WRITES_FROZEN`). If you get
a `201` instead, at least one instance hasn't picked up the flag — stop and
find it before continuing.

## Step 2 — Stop oms-main's OrderConfirmedShipmentConsumer entirely

This is the step Product/Customer's runbooks don't have, and it's not
optional — Step 1 alone does NOT stop this consumer. Scale down (or
redeploy with `app.process.role=web`) every oms-main instance that was
running with the worker role, so none of them hold membership in the
`oms-shipment-service` consumer group any longer.

**Confirm zero active members** before proceeding:

```
kafka-consumer-groups.sh --bootstrap-server <broker> \
  --describe --group oms-shipment-service
```

Every partition should show no active consumer. **Do not proceed to Step 4
until this is true** — starting shipment-service's own consumer while
oms-main's is still connected is exactly the partition-split hazard
described above.

Any `OrderConfirmed` events published while this group has zero members are
NOT lost — Kafka retains them, and whichever process reconnects to this
group next (shipment-service's own consumer, in Step 4) resumes from the
last committed offset. There is no gap to account for here, only a
processing delay for the duration of this window.

## Step 3 — Run the copy script

```
SOURCE_DSN=postgresql://<user>:<pass>@<oms-db-host>:5432/oms \
TARGET_DSN=postgresql://<user>:<pass>@<shipment-db-host>:5432/shipment_service \
./scripts/stage3-copy-shipments.sh
```

The script itself refuses to run against a non-empty target, and verifies
row count + a row-level checksum before printing `OK`. Unlike Product/
Customer, an empty source here isn't necessarily a mistake (see the
script's own note) — confirm deliberately either way. If it exits non-zero
for any reason, **stop — do not proceed.** See Rollback below.

## Step 4 — Independent spot-check (don't just trust the script)

```sql
-- On oms-main:
SELECT COUNT(*), MAX(id), MAX(updated_at) FROM oms_shipment.shipments;

-- On shipment-service:
SELECT COUNT(*), MAX(id) FROM shipments;
```

Counts and max id should match exactly. Spot-check 3-5 individual rows by
id on both sides (order_id, carrier, status, tracking_number, shipped_at,
delivered_at) — pick a mix of recently-updated and old, untouched rows.

## Step 5 — Confirm the sequence

```sql
-- On shipment-service:
SELECT last_value FROM shipments_id_seq;
```

Should equal the max id from Step 4. `INSERT INTO shipments (order_id,
carrier, status) VALUES (999999, 'OTHER', 'PENDING')` should get the next
id in sequence, not a collision — the script already runs `setval` for you,
this is just confirming it took. Delete that test row afterward.

## Step 6 — Start shipment-service (full role, consumer included)

With the data verified and oms-main's old consumer confirmed stopped
(Step 2), start/deploy shipment-service normally — its own
`OrderConfirmedShipmentConsumer` will join the (now-empty) `oms-shipment-service`
group and resume from the last committed offset.

## Step 7 — Confirm it's actually working

Don't just confirm the deploy succeeded:

- Confirm shipment-service's consumer group now shows active members:
  `kafka-consumer-groups.sh --describe --group oms-shipment-service`.
- Confirm the consumer group's lag is draining (not stuck) if any
  `OrderConfirmed` events queued up during Step 2/3's window.
- Place (or wait for) a real confirmed order and verify the resulting
  shipment lands in shipment-service's database, not oms-main's.
- Check Tempo for a trace on a manual shipment creation
  (`POST /shipments` against shipment-service) spanning shipment-service →
  oms-main (via `OrderClient`'s existence check) — confirms that path end
  to end.
- Watch the circuit breaker metrics for `orderClient` for a few minutes —
  should stay `CLOSED`.

## Step 8 — Decide the freeze flag's permanent state

**`app.shipment.writes-frozen` does not get toggled back to `false` after a
successful cutover.** shipment-service is now the source of truth for
shipment writes; oms-main's own `ShipmentController`/`ShipmentServiceImpl`/
`ShipmentAutoCreationServiceImpl` write paths are superseded, not restored.
Leave the flag `true` permanently in every environment from this point
forward — it now serves as a stop-gap preventing writes through the old
path (REST or Kafka) until Stage 5 physically removes the `shipment`
package (and `OrderConfirmedShipmentConsumer`) from oms-main.

If oms-main's `ShipmentController` needs to stay reachable for read-only
`GET` traffic in the meantime, no change is needed there — the freeze only
guards `createShipment`/`updateShipmentStatus`/`deleteShipment` in
`ShipmentServiceImpl` and `createForConfirmedOrder` in
`ShipmentAutoCreationServiceImpl`. Since Step 2 already stopped oms-main's
consumer entirely, that second guard should never actually fire in normal
operation from this point forward.

## Rollback

**Before Step 2 (freeze is on, consumer not yet stopped):** set
`app.shipment.writes-frozen=false`, redeploy oms-main, done. No data was
touched, and the consumer never stopped, so there's no gap to account for
either.

**Step 2 done, but before Step 3 (consumer stopped, copy not yet run):**
redeploy oms-main with the worker role restored (undoing Step 2's scale-down/
role change) so `OrderConfirmedShipmentConsumer` resumes — it picks back up
from the last committed offset, same as shipment-service would have. Then
unfreeze writes as above.

**Step 3 fails partway or verification fails:** same reasoning as Product/
Customer's scripts — Postgres's `COPY` is atomic per invocation, so a
failure means either zero rows landed or the script's own verification
already caught the mismatch. Either way:

```sql
-- On shipment-service, only if rows did land:
TRUNCATE shipments RESTART IDENTITY;
```

Then restore oms-main's consumer (as in the previous rollback case) and
unfreeze writes to resume normal operation while you investigate. Re-attempt
the whole window another time — don't try to resume mid-way through a
previous attempt.

**Step 6/7 — shipment-service's own consumer/API is broken or failing its
checks:** do NOT restart oms-main's `OrderConfirmedShipmentConsumer` at this
point if shipment-service's has already joined the group and consumed any
offset — restarting oms-main's consumer now would have IT pick up from
wherever shipment-service's left off, likely re-processing or skipping
events unpredictably relative to what's actually landed in which database.
Instead: fix shipment-service and get it healthy — the data copy and the
consumer handoff were both already fine at this point; only
shipment-service's own runtime health needs another look. Shipment writes
stay unavailable everywhere until it's fixed (existing shipment data copied
in Step 3 is still fully readable via oms-main's `GET` endpoints the whole
time, if those are still deployed).
