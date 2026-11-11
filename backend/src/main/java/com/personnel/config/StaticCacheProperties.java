package com.personnel.config;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "personnel.static-cache")
public class StaticCacheProperties {

    private boolean enabled = true;

    @Min(10)
    @Max(3600)
    private int ttlSeconds = 300;

    @Min(1)
    @Max(300)
    private int retrySeconds = 30;

    @Pattern(regexp = "[A-Za-z0-9:_-]{1,80}")
    private String namespace = "personnel:static:v1:";
}
