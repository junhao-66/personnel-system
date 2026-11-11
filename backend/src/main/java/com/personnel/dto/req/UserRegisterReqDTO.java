package com.personnel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterReqDTO(
    @NotBlank @Size(min = 4, max = 32) String userName,
    @NotBlank @Size(min = 6, max = 64) String password,
    @NotBlank @Size(max = 50) String realName,
    @Size(max = 20) String phone
) {}
