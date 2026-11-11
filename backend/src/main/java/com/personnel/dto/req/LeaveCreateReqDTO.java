package com.personnel.dto.req;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record LeaveCreateReqDTO(
    @NotBlank @Size(max = 20) String leaveType,
    @NotNull LocalDateTime startTime,
    @NotNull LocalDateTime endTime,
    @NotBlank @Size(max = 500) String reason
) {}
