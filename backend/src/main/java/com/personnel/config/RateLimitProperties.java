package com.personnel.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "personnel.rate-limit")
public class RateLimitProperties {

    @Min(1)
    @Max(100)
    private int passwordLimit = 5;

    @Min(1)
    @Max(100)
    private int aiLimit = 10;

    private boolean enabled = true;

    @Min(10)
    @Max(1000000)
    private int maxBuckets = 20000;

    @Min(1)
    @Max(86400)
    private int windowSeconds = 60;

    @Min(1)
    @Max(86400)
    private int registrationWindowSeconds = 3600;

    @Min(1)
    @Max(100000)
    private int credentialIpLimit = 60;

    @Min(1)
    @Max(100000)
    private int credentialAccountLimit = 10;

    @Min(1)
    @Max(100000)
    private int registrationIpLimit = 10;

    @Min(1)
    @Max(100000)
    private int registrationAccountLimit = 3;

    @Min(1)
    @Max(100000)
    private int readLimit = 240;

    @Min(1)
    @Max(100000)
    private int writeLimit = 60;

    @Min(1)
    @Max(100000)
    private int autoSaveLimit = 180;

    @Min(1)
    @Max(100000)
    private int attendanceLimit = 10;

    @Min(1)
    @Max(100000)
    private int leaveLimit = 30;

    @Min(1)
    @Max(100000)
    private int contentLimit = 10;

    @Min(1)
    @Max(100000)
    private int pdfLimit = 10;
}
