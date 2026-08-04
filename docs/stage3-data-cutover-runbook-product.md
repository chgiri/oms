# Stage 3 data cutover runbook — Product

> **STATUS: EXECUTED.** This cutover has been run and confirmed stable —
> product-service's database is now the sole source of truth for product
> data, and Stage 4 (`OrderServiceImpl`/`InventoryServiceImpl` now call
> `ProductClient` instead of the in-process `ProductService`) has been
> completed in oms-main. Stage 5 (deleting the `product` package from
> oms-main) has not happened yet — this document is kept as a historical
> record of the procedure that was followed, not a pending task.

Phase 4 (Product extraction) of the microservices-prep plan. This is the
maintenance-window procedure for migrating `oms_product.products` from
oms-main into product-service's own database.

**Read this in full before starting the window.** Steps are numbered but
several are irreversible-in-practice once done (see each step's "why this
order matters" note) — this is not a checklist to skim.

## Why Stage 3 and Stage 4 happen in the same window

Stage 3 (data cutover) and Stage 4 (swapping `OrderServiceImpl`/
`InventoryServiceImpl` to call `ProductClient` instead of the in-process
`ProductService`) are separate in *scope* but not in *execution*. If Stage 3
runs on its own, anything written to oms-main's `products` table between the
copy and Stage 4's deploy is invisible to product-service. So this window
covers both: freeze → copy → verify → deploy Stage 4 → confirm → done.

## Pre-window checklist

- [ ] Stage 2 is deployed and healthy in every environment this runbook
      targets (`ProductClient`, resilience config, tracing — see the Stage 2
      summary). Stage 4's deploy in step 6 depends on this already being live.
- [ ] product-service is deployed, migrated (its own `V1`/`V2` — empty
      `products` and `outbox_events` tables), and reachable from wherever
      `scripts/stage3-copy-products.sh` will run.
- [ ] `PRODUCT_SERVICE_URL` is set correctly in every oms-main environment
      config that Stage 4's deploy will use.
- [ ] `AUTH_SERVICE_JWKS_URI` on product-service points at the correct
      oms-main JWKS endpoint for this environment.
- [ ] A rehearsal of `scripts/stage3-copy-products.sh` has been run against a
      staging copy of production-shaped data at least once, successfully.
- [ ] Everyone who might deploy to oms-main during the window knows not to.

## Step 1 — Freeze Product writes in oms-main

Set `app.product.writes-frozen=true` (env var `PRODUCT_WRITES_FROZEN=true`)
and redeploy **every** running oms-main instance — not a live config
refresh. Redeploying, not refreshing, is deliberate: it's the only way to be
certain every instance actually picked up the new value before you proceed,
rather than trusting a config-reload mechanism you'd be verifying for the
first time under a live migration.

**Why this order matters:** everything after this step assumes the source
data is frozen. If even one instance is still serving writes, the row count/
checksum verification in step 3 can pass on stale data and still miss a row
written after the snapshot — the checksum only tells you the copy matches
what it saw, not that nothing changed after.

**Confirm the freeze took effect** before proceeding — don't just trust the
deploy succeeded:

```
curl -X POST https://<oms-main>/api/v1/products \
  -H "Authorization: Bearer <any valid token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"freeze-check","price":1.00}'
```

Expect `503` with `errorCode: EPR501` (`PRODUCT_WRITES_FROZEN`). If you get a
`201` instead, at least one instance hasn't picked up the flag — stop and
find it before continuing.

## Step 2 — Run the copy script

```
SOURCE_DSN=postgresql://<user>:<pass>@<oms-db-host>:5432/oms \
TARGET_DSN=postgresql://<user>:<pass>@<product-db-host>:5432/product_service \
./scripts/stage3-copy-products.sh
```

The script itself refuses to run against a non-empty target, refuses an
empty source, and verifies row count + a row-level checksum before printing
`OK`. If it exits non-zero for any reason, **stop — do not proceed to step
3.** See Rollback below.

## Step 3 — Independent spot-check (don't just trust the script)

The script's own checksum is the primary verification, but run these two
queries by hand too — cheap insurance against a bug in the script itself
being the thing that makes its own check pass:

```sql
-- On oms-main:
SELECT COUNT(*), MAX(id), MAX(updated_at) FROM oms_product.products;

-- On product-service:
SELECT COUNT(*), MAX(id) FROM products;
```

Counts and max id should match exactly. Spot-check 3-5 individual rows by id
on both sides (name, price, status) — pick a mix of recently-updated and
old, untouched rows.

## Step 4 — Confirm the sequence

```sql
-- On product-service:
SELECT last_value FROM products_id_seq;
```

Should equal the max id from step 3. `INSERT INTO products (name, price)
VALUES ('sequence-check', 1.00)` should get the next id in sequence, not a
collision — the script already runs `setval` for you, this is just
confirming it took.

## Step 5 — Deploy Stage 4

With the data verified in place, deploy oms-main with `OrderServiceImpl`/
`InventoryServiceImpl` wired to `ProductClient` instead of the in-process
`ProductService`. (If Stage 4 isn't built yet, stop here — do not unfreeze
writes and leave the window open indefinitely; see Rollback.)

## Step 6 — Confirm Stage 4 is actually working

Don't just confirm the deploy succeeded — confirm product data is actually
flowing through the new path:

- Place a real order for a product that only exists because of the Stage 3
  copy (i.e., wasn't independently created directly in product-service) —
  confirms `OrderServiceImpl` is really reaching product-service, not
  silently still using stale in-process data.
- Check Tempo for a trace spanning oms-main → product-service on that
  request (see Stage 2's tracing work) — confirms the whole path end to end,
  not just that *a* response came back.
- Watch the circuit breaker metrics for `productClient` for a few minutes —
  should stay `CLOSED`.

## Step 7 — Decide the freeze flag's permanent state

**`app.product.writes-frozen` does not get toggled back to `false` after a
successful cutover.** Product-service is now the source of truth for writes;
oms-main's own `ProductController`/`ProductServiceImpl` write path is
superseded, not restored. Leave the flag `true` permanently in every
environment from this point forward — it now serves as a stop-gap
preventing writes through the old path until Stage 5 physically removes
`ProductController`'s create/update/delete endpoints (and eventually the
whole `product` package) from oms-main.

If oms-main's `ProductController` needs to stay reachable for read-only
`GET` traffic in the meantime, no change is needed there — the freeze only
guards `createProduct`/`updateProduct`/`deleteProduct` in
`ProductServiceImpl`.

## Rollback

**Before step 2 runs (copy hasn't started):** set
`app.product.writes-frozen=false`, redeploy oms-main, done. No data was
touched.

**Step 2 fails partway or verification fails in step 2/3:** the copy script
itself doesn't leave inconsistent state — Postgres's `COPY` is atomic per
invocation, so a failure means either zero rows landed or the script's own
verification already caught the mismatch and exited non-zero before telling
you to proceed. Either way:

```sql
-- On product-service, only if rows did land:
TRUNCATE products RESTART IDENTITY;
```

Then set `app.product.writes-frozen=false` and redeploy oms-main to resume
normal operation while you investigate. Re-attempt the whole window another
time — don't try to resume mid-way through a previous attempt.

**Step 5/6 — Stage 4 deploy is broken or failing its checks:** roll oms-main
back to the pre-Stage-4 build. **Leave `app.product.writes-frozen=true`** —
don't unfreeze just because Stage 4 didn't work out; the data copy itself
was fine, only the call-site cutover needs another attempt. Product writes
stay unavailable through oms-main until Stage 4 is fixed and redeployed
(existing product data is still fully readable the whole time — this only
blocks create/update/delete).
