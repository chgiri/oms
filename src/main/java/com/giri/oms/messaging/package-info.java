/**
 * The sanctioned cross-module communication mechanism (domain events +
 * transactional outbox), not a business module with its own private storage
 * to protect from other modules — every module that publishes or consumes an
 * event needs messaging.event and messaging.outbox, so it's marked fully
 * OPEN rather than maintained as a per-package NamedInterface list.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.giri.oms.messaging;
