/**
 * Stage 2 of the microservices-prep plan (Phase 4, Customer extraction): the
 * monolith's client to customer-service's HTTP API. Mirrors
 * {@code productclient} exactly (see that module's Javadoc for the full
 * reasoning — repeated only briefly here to avoid drift between two copies
 * of the same prose). This module does NOT yet replace anything —
 * OrderServiceImpl still calls the in-process {@code customer} module's
 * CustomerService; wiring this client into that call site is Stage 4.
 * <p>
 * Same public-surface convention as every other module here — see
 * {@link com.giri.oms.ModularityTests}' Javadoc — {@code service} (the
 * {@link com.giri.oms.customerclient.service.CustomerClient} interface,
 * never {@code service.impl}), {@code dto}, and {@code exception} are the
 * only sub-packages opened via {@code @NamedInterface}; {@code config}
 * stays internal.
 * <p>
 * Deliberately its own module and its own copy of the auth-forwarding
 * interceptor pattern, not shared with {@code productclient} — same
 * reasoning as product-service/customer-service each getting their own
 * local outbox rather than sharing one: once Stage 5 deletes the
 * {@code customer} package outright, this module needs to survive that
 * deletion cleanly on its own, with no coupling back to a sibling client
 * module that has nothing to do with this extraction.
 */
package com.giri.oms.customerclient;
