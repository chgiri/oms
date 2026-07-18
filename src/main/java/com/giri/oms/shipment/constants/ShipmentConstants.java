package com.giri.oms.shipment.constants;

public final class ShipmentConstants {

    private ShipmentConstants() {
        // utility class — no instances
    }

    // ---- Exception messages (used with String.format) ----
    // SHIPMENT_NOT_FOUND message now lives in com.giri.oms.common.exception.ErrorCode
    // alongside its error code.
    public static final String INVALID_STATUS_TRANSITION_MESSAGE =
            "Cannot transition shipment id %d from status %s to %s";
    public static final String SHIPMENT_NOT_DELETABLE_MESSAGE =
            "Shipment id %d cannot be deleted while in status %s — only PENDING or RETURNED shipments can be deleted";

    // ---- Bean Validation messages ----
    public static final String ORDER_ID_REQUIRED_MESSAGE = "Order ID must not be null";
    public static final String CARRIER_REQUIRED_MESSAGE = "Carrier must not be null";
    public static final String STATUS_REQUIRED_MESSAGE = "Status must not be null";

    // ---- Success / log messages ----
    public static final String SHIPMENT_CREATED_LOG = "Shipment created successfully with id: {}";
    public static final String SHIPMENT_STATUS_UPDATED_LOG = "Shipment id: {} status updated to: {}";
    public static final String SHIPMENT_DELETED_LOG = "Shipment deleted successfully with id: {}";
}
