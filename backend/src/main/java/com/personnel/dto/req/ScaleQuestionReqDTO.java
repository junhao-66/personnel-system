package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record ScaleQuestionReqDTO(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String key,
    @NotBlank @Size(max = 300) String leftText,
    @NotBlank @Size(max = 300) String rightText,
    @NotBlank @Pattern(regexp = "EI|SN|TF|JP") String dimension,
    @NotBlank @Pattern(regexp = "[EISNTFJP]") String highPole,
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
        using = com.personnel.common.util.AssessmentIntegerDeserializer.class
    )
    @NotNull
    @Min(1)
    @Max(10)
    Integer weight
) {}
