package com.personnel.dto.req;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personnel.common.util.AssessmentIntegerDeserializer;
import jakarta.validation.constraints.*;
import java.util.Map;

public record AssessmentAnswersReqDTO(
    @NotNull @Min(0) Integer revision,
    @JsonDeserialize(contentUsing = AssessmentIntegerDeserializer.class)
    @NotNull
    @Size(max = 128)
    Map<@Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String, @NotNull @Min(1) @Max(5) Integer> answers
) {}
