package com.personnel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PositionSaveReqDTO(
    @NotBlank @Size(max = 100) String positionName,
    @NotBlank @Size(max = 50) String positionCode,
    Long departmentId,
    @Size(max = 255) String description
) {}
