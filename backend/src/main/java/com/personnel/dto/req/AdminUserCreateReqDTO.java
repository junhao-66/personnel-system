package com.personnel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserCreateReqDTO(
    @NotBlank @Size(min = 4, max = 32) String userName,
    @NotBlank @Size(min = 6, max = 64) String password,
    @NotBlank @Size(max = 50) String realName,
    @Size(max = 20) String phone,
    @NotBlank String roleCode,
    Long departmentId,
    Long positionId
) {}
