package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record AssessmentStartReqDTO(
    @NotNull @Positive Long scaleId,
    @NotNull @AssertTrue Boolean consentAccepted
) {}
