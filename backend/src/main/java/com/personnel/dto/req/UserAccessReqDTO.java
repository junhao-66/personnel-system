package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record UserAccessReqDTO(
    @NotBlank @Pattern(regexp = "ADMIN|DEPARTMENT_MANAGER|EMPLOYEE") String roleCode,
    @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String userStatus
) {}
