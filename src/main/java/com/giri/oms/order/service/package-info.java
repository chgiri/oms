/**
 * The order module's public API (OrderService). `service.impl` stays internal.
 *
 * payment and shipment both call through here now (getOrderById for existence
 * checks, assertAwaitingPayment for payment's status precondition) instead of
 * reaching into order.entity/order.repository directly.
 */
@org.springframework.modulith.NamedInterface
package com.giri.oms.order.service;