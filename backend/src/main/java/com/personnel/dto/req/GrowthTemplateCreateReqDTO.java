package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record GrowthTemplateCreateReqDTO(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String version,
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 500) String suggestedCycle,
    @NotBlank @Size(max = 1000) String observationTarget,
    @NotBlank @Size(max = 1000) String reviewPrompt,
    @NotBlank @Size(max = 200) String fallbackContext,
    @NotBlank @Size(max = 1000) String fallbackAction
) {}
