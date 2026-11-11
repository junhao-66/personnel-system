package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record AiQuestionReqDTO(@NotBlank @Size(max = 500) String question) {}
