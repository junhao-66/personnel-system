package com.personnel.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SystemSettingsUpdateReqDTO(
    @NotNull Boolean assessmentEnabled,
    @NotNull @Size(max = 500) String assessmentNotice,
    @NotNull Boolean announcementEnabled,
    @NotNull @Size(max = 100) String announcementTitle,
    @NotNull @Size(max = 2000) String announcementContent,
    @NotNull @Min(0) Integer revision
) {}
