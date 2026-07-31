-- Stage 5 of the microservices-prep plan (Phase 4, Shipment extraction):
-- shipment-service's cutover (see docs/stage3-data-cutover-runbook-shipment.md)
-- has been confirmed stable, and the shipment package itself has been
-- deleted from this codebase (see ModularityTests/GlobalExceptionHandler/
-- ErrorCode's updated comments). Nothing in this application reads or
-- writes oms_shipment.shipments anymore — shipment-service's own database
-- is the sole owner of that data now.
--
-- Per the runbook's own rollback guidance, do NOT run this migration until
-- shipment-service has been live and confirmed healthy for a reasonable
-- burn-in period — this is intentionally the last, hardest-to-reverse step
-- of the cutover (V7's original CREATE TABLE stays in this migration
-- history untouched either way, same as every other historical migration
-- in this project — only the live schema is being dropped here, not the
-- record of how it got here).

DROP SCHEMA oms_shipment CASCADE;
