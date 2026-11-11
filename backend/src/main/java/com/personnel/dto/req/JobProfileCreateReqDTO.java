package com.personnel.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record JobProfileCreateReqDTO(
    @NotNull @Positive Long positionId,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String version,
    @NotBlank @Size(max = 100) String title,
    @NotNull @Positive Long graphId,
    @NotBlank @Size(max = 1000) String attribution,
    @NotNull @Size(min = 1, max = 8) List<@NotNull @Valid RuleWeight> rules
) {
    public record RuleWeight(@NotBlank String ruleId, @NotNull @Min(1) @Max(100) Integer weight) {}
}
