package com.giri.oms.inventory.exception;

import com.giri.oms.inventory.constants.InventoryConstants;

/**
 * Thrown when there isn't enough available stock, across any location, to satisfy a
 * reservation request. This is a business-rule failure, not an infrastructure one —
 * retrying the same message will never produce more stock — so the Kafka consumer's
 * error handler is configured to treat this as non-retryable (see KafkaConsumerConfig).
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super(String.format(InventoryConstants.INSUFFICIENT_STOCK_MESSAGE, productId, requested, available));
    }
}
