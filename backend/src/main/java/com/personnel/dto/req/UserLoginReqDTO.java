package com.personnel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserLoginReqDTO(
    @NotBlank @Size(max = 32) String userName,
    @NotBlank @Size(max = 64) String password
) {}
