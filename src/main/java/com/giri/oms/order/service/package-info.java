/**
 * The order module's public API (OrderService). `service.impl` stays internal.
 *
 * payment calls through here now (assertAwaitingPayment for payment's status
 * precondition) instead of reaching into order.entity/order.repository
 * directly. Shipment used to as well (getOrderById for its existence check)
 * until Stage 5 of the microservices-prep plan extracted it into
 * shipment-service — that existence check is now an HTTP call via
 * shipment-service's own OrderClient instead of an in-process call through
 * this interface.
 */
@org.springframework.modulith.NamedInterface
package com.giri.oms.order.service;