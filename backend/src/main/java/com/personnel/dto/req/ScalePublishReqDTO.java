package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record ScalePublishReqDTO(
    @NotNull @AssertTrue Boolean commercialUseVerified,
    @NotNull @AssertTrue Boolean translationVerified,
    @NotNull @AssertTrue Boolean scoringVerified
) {}
