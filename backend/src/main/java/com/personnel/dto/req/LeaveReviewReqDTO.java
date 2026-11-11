package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record LeaveReviewReqDTO(
    @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String status,
    @Size(max = 500) String reviewComment
) {}
