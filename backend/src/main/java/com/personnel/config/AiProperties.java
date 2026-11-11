package com.personnel.config;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "personnel.ai")
public class AiProperties {

    @Pattern(regexp = "MOCK|REMOTE|OFF")
    private String mode = "MOCK";

    private boolean externalEnabled = false;
    private String endpoint = "https://api.openai.com/v1/responses";
    private String model = "";

    @lombok.ToString.Exclude
    private String apiKey = "";

    @Min(1)
    @Max(30)
    private int timeoutSeconds = 8;
}
