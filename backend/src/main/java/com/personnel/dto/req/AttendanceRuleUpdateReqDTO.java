package com.personnel.dto.req;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record AttendanceRuleUpdateReqDTO(
    @NotNull LocalTime workStartTime,
    @NotNull LocalTime workEndTime
) {}
