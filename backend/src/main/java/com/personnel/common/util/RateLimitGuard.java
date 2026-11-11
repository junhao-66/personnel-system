package com.personnel.common.util;

import com.personnel.common.exception.RateLimitExceededException;
import com.personnel.config.RateLimitProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Infrastructure guard; keys are hashes, and no credentials or subject values are logged. */
@Component
public class RateLimitGuard {

    private final RateLimitProperties properties;
    private final LocalRateLimiter limiter;

    public RateLimitGuard(RateLimitProperties properties) {
        this.properties = properties;
        this.limiter = new LocalRateLimiter(properties.getMaxBuckets());
    }

    public void check(String group, String subject, int limit, int seconds) {
        if (!properties.isEnabled()) return;
        long retry = limiter.acquire(group + ":" + digest(subject), limit, seconds);
        if (retry > 0) throw new RateLimitExceededException(retry);
    }

    public void checkCredentials(String account) {
        check(
            "CREDENTIAL_ACCOUNT",
            account.trim().toLowerCase(Locale.ROOT),
            properties.getCredentialAccountLimit(),
            properties.getWindowSeconds()
        );
    }

    public void checkKnownAccount(Long id) {
        // Database collations may treat different spellings as one account; resolved ID closes that bypass.
        check(
            "CREDENTIAL_ACCOUNT_ID",
            id.toString(),
            properties.getCredentialAccountLimit(),
            properties.getWindowSeconds()
        );
    }

    public void checkRegistration(String account) {
        check(
            "REGISTRATION_ACCOUNT",
            account.trim().toLowerCase(Locale.ROOT),
            properties.getRegistrationAccountLimit(),
            properties.getRegistrationWindowSeconds()
        );
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
