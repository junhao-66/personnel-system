package com.personnel.vo;

import java.time.LocalDateTime;

/** Only approved coverage times; no reason or reviewer details cross into attendance. */
public record LeavePeriodVO(Long userId, LocalDateTime startTime, LocalDateTime endTime) {}
