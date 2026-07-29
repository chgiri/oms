# Stage 3 data cutover runbook — Customer

Phase 4 (Customer extraction) of the microservices-prep plan. This is the
maintenance-window procedure for migrating `oms_customer.customers` from
oms-main into customer-service's own database. Mirrors
`stage3-data-cutover-runbook.md` (Product) exactly — see that document if
anything here is ambiguous, since the two are meant to stay in lockstep.

**Read this in full before starting the window.** Steps are numbered but
several are irreversible-in-practice once done (see each step's "why this
order matters" note) — this is not a checklist to skim.

## Why Stage 3 and Stage 4 happen in the same window

Stage 3 (data cutover) and Stage 4 (swapping `OrderServiceImpl` to call
`CustomerClient` instead of the in-process `CustomerService`) are separate in
*scope* but not in *execution*. If Stage 3 runs on its own, anything written
to oms-main's `customers` table between the copy and Stage 4's deploy is
invisible to customer-service. So this window covers both: freeze → copy →
verify → deploy Stage 4 → confirm → done.

## Pre-window checklist

- [ ] Stage 2 is deployed and healthy in every environment this runbook
      targets (`CustomerClient`, resilience config, tracing — see the Stage 2
      summary). Stage 4's deploy in step 6 depends on this already being live.
- [ ] customer-service is deployed, migrated (its own `V1`/`V2`/`V3` — empty
      `customers` and `outbox_events` tables), and reachable from wherever
      `scripts/stage3-copy-customers.sh` will run.
- [ ] `CUSTOMER_SERVICE_URL` is set correctly in every oms-main environment
      config that Stage 4's deploy will use.
- [ ] `AUTH_SERVICE_JWKS_URI` on customer-service points at the correct
      oms-main JWKS endpoint for this environment.
- [ ] A rehearsal of `scripts/stage3-copy-customers.sh` has been run against a
      staging copy of production-shaped data at least once, successfully.
- [ ] Everyone who might deploy to oms-main during the window knows not to.

## Step 1 — Freeze Customer writes in oms-main

Set `app.customer.writes-frozen=true` (env var `CUSTOMER_WRITES_FROZEN=true`)
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
curl -X POST https://<oms-main>/api/v1/customers \
  -H "Authorization: Bearer <any valid token>" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"freeze","lastName":"check","email":"freeze-check@example.com"}'
```

Expect `503` with `errorCode: ECU501` (`CUSTOMER_WRITES_FROZEN`). If you get
a `201` instead, at least one instance hasn't picked up the flag — stop and
find it before continuing.

## Step 2 — Run the copy script

```
SOURCE_DSN=postgresql://<user>:<pass>@<oms-db-host>:5432/oms \
TARGET_DSN=postgresql://<user>:<pass>@<customer-db-host>:5432/customer_service \
./scripts/stage3-copy-customers.sh
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
SELECT COUNT(*), MAX(id), MAX(updated_at) FROM oms_customer.customers;

-- On customer-service:
SELECT COUNT(*), MAX(id) FROM customers;
```

Counts and max id should match exactly. Spot-check 3-5 individual rows by id
on both sides (first name, last name, email, status) — pick a mix of
recently-updated and old, untouched rows. Email uniqueness is enforced on
both sides identically, so a mismatch here (duplicate emails that somehow
diverged) would surface as a copy failure in step 2, not silently here — but
worth eyeballing anyway.

## Step 4 — Confirm the sequence

```sql
-- On customer-service:
SELECT last_value FROM customers_id_seq;
```

Should equal the max id from step 3. `INSERT INTO customers (first_name,
last_name, email) VALUES ('sequence', 'check', 'sequence-check@example.com')`
should get the next id in sequence, not a collision — the script already
runs `setval` for you, this is just confirming it took.

## Step 5 — Deploy Stage 4

With the data verified in place, deploy oms-main with `OrderServiceImpl`
wired to `CustomerClient` instead of the in-process `CustomerService`. (If
Stage 4 isn't built yet, stop here — do not unfreeze writes and leave the
window open indefinitely; see Rollback.)

## Step 6 — Confirm Stage 4 is actually working

Don't just confirm the deploy succeeded — confirm customer data is actually
flowing through the new path:

- Place a real order for a customer that only exists because of the Stage 3
  copy (i.e., wasn't independently created directly in customer-service) —
  confirms `OrderServiceImpl` is really reaching customer-service, not
  silently still using stale in-process data.
- Check Tempo for a trace spanning oms-main → customer-service on that
  request (see Stage 2's tracing work) — confirms the whole path end to end,
  not just that *a* response came back.
- Watch the circuit breaker metrics for `customerClient` for a few minutes —
  should stay `CLOSED`.

## Step 7 — Decide the freeze flag's permanent state

**`app.customer.writes-frozen` does not get toggled back to `false` after a
successful cutover.** Customer-service is now the source of truth for
writes; oms-main's own `CustomerController`/`CustomerServiceImpl` write path
is superseded, not restored. Leave the flag `true` permanently in every
environment from this point forward — it now serves as a stop-gap
preventing writes through the old path until Stage 5 physically removes
`CustomerController`'s create/update/delete endpoints (and eventually the
whole `customer` package) from oms-main.

If oms-main's `CustomerController` needs to stay reachable for read-only
`GET` traffic in the meantime, no change is needed there — the freeze only
guards `createCustomer`/`updateCustomer`/`deleteCustomer` in
`CustomerServiceImpl`.

## Rollback

**Before step 2 runs (copy hasn't started):** set
`app.customer.writes-frozen=false`, redeploy oms-main, done. No data was
touched.

**Step 2 fails partway or verification fails in step 2/3:** the copy script
itself doesn't leave inconsistent state — Postgres's `COPY` is atomic per
invocation, so a failure means either zero rows landed or the script's own
verification already caught the mismatch and exited non-zero before telling
you to proceed. Either way:

```sql
-- On customer-service, only if rows did land:
TRUNCATE customers RESTART IDENTITY;
```

Then set `app.customer.writes-frozen=false` and redeploy oms-main to resume
normal operation while you investigate. Re-attempt the whole window another
time — don't try to resume mid-way through a previous attempt.

**Step 5/6 — Stage 4 deploy is broken or failing its checks:** roll oms-main
back to the pre-Stage-4 build. **Leave `app.customer.writes-frozen=true`** —
don't unfreeze just because Stage 4 didn't work out; the data copy itself
was fine, only the call-site cutover needs another attempt. Customer writes
stay unavailable through oms-main until Stage 4 is fixed and redeployed
(existing customer data is still fully readable the whole time — this only
blocks create/update/delete).
