package com.personnel.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserAuditReqDTO(
    @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String auditStatus,
    @Size(max = 500) String auditReason
) {
    public UserAuditReqDTO(String auditStatus) {
        this(auditStatus, null);
    }
}
