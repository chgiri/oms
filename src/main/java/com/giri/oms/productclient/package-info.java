/**
 * Stage 2 of the microservices-prep plan (Phase 4, Product extraction): the
 * monolith's client to product-service's HTTP API. This module does NOT yet
 * replace anything — OrderServiceImpl and InventoryServiceImpl still call
 * the in-process {@code product} module's ProductService (see that module's
 * own Javadoc); wiring this client into those call sites is Stage 4.
 * <p>
 * Follows the same public-surface convention as every other module here —
 * see {@link com.giri.oms.ModularityTests}' Javadoc — {@code service}
 * (the {@link com.giri.oms.productclient.service.ProductClient} interface,
 * never {@code service.impl}), {@code dto}, and {@code exception} are the
 * only sub-packages opened via {@code @NamedInterface}; {@code config}
 * (the RestClient/resilience4j wiring and the auth-forwarding interceptor)
 * stays internal.
 * <p>
 * Deliberately its own module, not folded into {@code product}: once Stage 5
 * deletes the {@code product} package outright, this is the module that
 * survives as the sole way anything in this codebase reaches product data —
 * keeping it separate from day one means Stage 5 is a deletion, not a
 * refactor.
 */
package com.giri.oms.productclient;
