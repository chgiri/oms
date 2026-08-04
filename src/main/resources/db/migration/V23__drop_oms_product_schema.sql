-- Stage 5 of the microservices-prep plan (Phase 4, Product extraction):
-- product-service's cutover (see docs/stage3-data-cutover-runbook-product.md)
-- has been confirmed stable, and the product package itself has been
-- deleted from this codebase (see ModularityTests/GlobalExceptionHandler/
-- ErrorCode's updated comments — and ProductNotFoundException's relocation
-- to productclient.exception, since ProductClient's not-found contract
-- still needed it even though the rest of the module didn't survive).
-- Nothing in this application reads or writes oms_product.products anymore
-- — product-service's own database is the sole owner of that data now.
--
-- Per the runbook's own rollback guidance, do NOT run this migration until
-- product-service has been live and confirmed healthy for a reasonable
-- burn-in period — this is intentionally the last, hardest-to-reverse step
-- of the cutover (V1__init_schema.sql's original products table creation
-- stays in this migration history untouched either way, same as every
-- other historical migration in this project — only the live schema is
-- being dropped here, not the record of how it got here).

DROP SCHEMA oms_product CASCADE;
