package com.giri.oms.inventory.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;

/**
 * Thrown when there isn't enough available stock, across any location, to satisfy a
 * reservation request. This is a business-rule failure, not an infrastructure one —
 * retrying the same message will never produce more stock — so the Kafka consumer's
 * error handler is configured to treat this as non-retryable (see KafkaConsumerConfig).
 */
public class InsufficientStockException extends RuntimeException implements ErrorCoded {

    public InsufficientStockException(Long productId, int requested, int available) {
        super(ErrorCode.INSUFFICIENT_STOCK.formatMessage(productId, requested, available));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.INSUFFICIENT_STOCK;
    }
}
