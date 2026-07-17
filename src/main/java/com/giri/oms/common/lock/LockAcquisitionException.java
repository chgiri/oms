package com.giri.oms.common.lock;

/**
 * Thrown when a distributed lock can't be acquired in time (contention) or the wait
 * is interrupted. Mapped to HTTP 409 by GlobalExceptionHandler — the caller should
 * retry, since it reflects a concurrent update in progress rather than a bad request.
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String lockKey) {
        super("Could not acquire lock for '" + lockKey + "' — another update is in progress, please retry");
    }

    public LockAcquisitionException(String lockKey, Throwable cause) {
        super("Interrupted while waiting for lock '" + lockKey + "'", cause);
    }
}
