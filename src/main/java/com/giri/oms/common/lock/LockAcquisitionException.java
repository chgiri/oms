package com.giri.oms.common.lock;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;

/**
 * Thrown when a distributed lock can't be acquired in time (contention) or the wait
 * is interrupted. Mapped to HTTP 409 by GlobalExceptionHandler — the caller should
 * retry, since it reflects a concurrent update in progress rather than a bad request.
 */
public class LockAcquisitionException extends RuntimeException implements ErrorCoded {

    public LockAcquisitionException(String lockKey) {
        super(ErrorCode.LOCK_ACQUISITION_FAILED.formatMessage(lockKey));
    }

    public LockAcquisitionException(String lockKey, Throwable cause) {
        super("Interrupted while waiting for lock '" + lockKey + "'", cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.LOCK_ACQUISITION_FAILED;
    }
}
