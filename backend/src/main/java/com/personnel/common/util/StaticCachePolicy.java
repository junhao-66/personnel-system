package com.personnel.common.util;

import com.personnel.config.StaticCacheProperties;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Cache static catalog data only; transactional report generation always reads MySQL. */
@Component("staticCachePolicy")
public class StaticCachePolicy {

    private final StaticCacheProperties properties;
    private final Clock clock;
    private final AtomicLong retryAt = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong revision = new AtomicLong();
    private final String runId = java.util.UUID.randomUUID().toString();

    public StaticCachePolicy(StaticCacheProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean available() {
        return (
            properties.isEnabled() &&
            clock.millis() >= retryAt.get() &&
            !TransactionSynchronizationManager.isActualTransactionActive()
        );
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public void failed() {
        failures.incrementAndGet();
        advance();
        retryAt.set(clock.millis() + properties.getRetrySeconds() * 1000L);
    }

    public boolean circuitOpen() {
        return clock.millis() < retryAt.get();
    }

    public long failures() {
        return failures.get();
    }

    public String key(String logical) {
        return runId + ":" + revision.get() + ":" + logical;
    }

    public void advance() {
        revision.incrementAndGet();
    }

    public void retryNow() {
        retryAt.set(0);
    }
}
