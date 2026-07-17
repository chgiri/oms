package com.giri.oms.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Wraps a critical section in a Redisson distributed lock. The app has more than one
 * instance in production, and a plain Java `synchronized` only protects a single JVM —
 * two instances can still race on the same database row. This gives every instance a
 * shared mutex keyed by whatever the caller is protecting (e.g. an inventory record id).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    /**
     * Runs {@code action} while holding the lock for {@code lockKey}. Waits up to
     * {@code waitTime} to acquire it; once acquired, the lock auto-expires after
     * {@code leaseTime} even if this instance crashes mid-operation, so a dead node can
     * never wedge the lock forever.
     *
     * @throws LockAcquisitionException if the lock couldn't be acquired within waitTime
     */
    public <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(lockKey, ex);
        }

        if (!acquired) {
            log.warn("Could not acquire distributed lock for key: {} within {}", lockKey, waitTime);
            throw new LockAcquisitionException(lockKey);
        }

        try {
            return action.get();
        } finally {
            // Only release if this thread is still the owner — tryLock's own lease
            // may have already expired and been picked up by someone else, in which
            // case unlocking here would release a lock we no longer hold.
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
