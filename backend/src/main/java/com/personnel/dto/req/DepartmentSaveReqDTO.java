package com.personnel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentSaveReqDTO(
    @NotBlank @Size(max = 100) String departmentName,
    @NotBlank @Size(max = 50) String departmentCode,
    Long managerUserId,
    Integer sortOrder
) {}
