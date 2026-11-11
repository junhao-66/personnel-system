package com.personnel.common;

import com.personnel.common.util.LocalRateLimiter;
import com.personnel.common.util.RateLimitGuard;
import com.personnel.common.exception.RateLimitExceededException;
import com.personnel.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class LocalRateLimiterTest {
    @Test void thresholdAndExactExpiryDoNotExtendOnRejection() {
        var clock = new AtomicLong(); var limiter = new LocalRateLimiter(10, clock::get);
        assertThat(limiter.acquire("a", 1, 10)).isZero();
        clock.set(1_100_000_000L);
        assertThat(limiter.acquire("a", 1, 10)).isEqualTo(9);
        clock.set(9_900_000_000L);
        assertThat(limiter.acquire("a", 1, 10)).isEqualTo(1);
        clock.set(10_000_000_000L);
        assertThat(limiter.acquire("a", 1, 10)).isZero();
    }
    @Test void saturatedCapacityDoesNotEvictActiveBucketsAndReclaimsExpiredOnes() {
        var clock = new AtomicLong(); var limiter = new LocalRateLimiter(1, clock::get);
        assertThat(limiter.acquire("a", 1, 2)).isZero();
        assertThat(limiter.acquire("b", 1, 2)).isPositive();
        assertThat(limiter.acquire("a", 1, 2)).isEqualTo(2);
        clock.set(2_000_000_000L);
        assertThat(limiter.acquire("b", 1, 2)).isZero();
    }
    @Test void independentKeysHaveIndependentAllowances() {
        var limiter = new LocalRateLimiter(10);
        assertThat(limiter.acquire("a", 1, 60)).isZero();
        assertThat(limiter.acquire("b", 1, 60)).isZero();
        assertThat(limiter.acquire("a", 1, 60)).isPositive();
    }
    @Test void concurrentRequestsCannotExceedThreshold() throws Exception {
        var limiter = new LocalRateLimiter(10); var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = IntStream.range(0, 100).<Callable<Long>>mapToObj(i -> () -> limiter.acquire("a", 10, 60)).toList();
            long admitted = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get() == 0) admitted++;
            assertThat(admitted).isEqualTo(10);
        } finally { executor.shutdownNow(); }
    }
    @Test void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new LocalRateLimiter(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalRateLimiter(1).acquire("a", 0, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalRateLimiter(1).acquire("a", 1, 86401)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void normalizedCredentialsShareAllowanceButRegistrationIsSeparate() {
        var properties = new RateLimitProperties(); properties.setCredentialAccountLimit(1);
        var guard = new RateLimitGuard(properties);
        guard.checkCredentials(" Alice ");
        assertThatThrownBy(() -> guard.checkCredentials("alice")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> guard.checkRegistration("alice")).doesNotThrowAnyException();
    }
    @Test void resolvedAccountIdAndDisabledModeAreEnforced() {
        var properties = new RateLimitProperties(); properties.setCredentialAccountLimit(1);
        var guard = new RateLimitGuard(properties); guard.checkKnownAccount(1L);
        assertThatThrownBy(() -> guard.checkKnownAccount(1L)).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> guard.checkKnownAccount(2L)).doesNotThrowAnyException();
        properties.setEnabled(false);
        assertThatCode(() -> guard.checkKnownAccount(1L)).doesNotThrowAnyException();
    }
}
