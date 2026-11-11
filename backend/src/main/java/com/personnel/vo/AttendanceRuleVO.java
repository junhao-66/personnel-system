package com.personnel.vo;

import java.time.LocalTime;

public record AttendanceRuleVO(
    LocalTime workStartTime,
    LocalTime workEndTime,
    String timeZone,
    boolean weekendRest
) {}
