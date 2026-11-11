package com.personnel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountPasswordReqDTO(
    @NotBlank @Size(max = 64) String currentPassword,
    @NotBlank @Size(min = 8, max = 64) String newPassword
) {
    @Override
    public String toString() {
        return "AccountPasswordReqDTO[REDACTED]";
    }
}
