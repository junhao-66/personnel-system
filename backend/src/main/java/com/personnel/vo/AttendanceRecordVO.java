package com.personnel.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceRecordVO(
    Long id,
    Long userId,
    LocalDate attendanceDate,
    LocalDateTime clockInTime,
    LocalDateTime clockOutTime,
    LocalTime workStartTime,
    LocalTime workEndTime,
    boolean restDay,
    boolean late,
    boolean early,
    String status,
    long approvedLeaveMinutes,
    boolean missingIn,
    boolean missingOut,
    LocalDateTime requiredStart,
    LocalDateTime requiredEnd,
    String scheduleBasis
) {
    public AttendanceRecordVO(
        Long id,
        Long userId,
        LocalDate attendanceDate,
        LocalDateTime clockInTime,
        LocalDateTime clockOutTime,
        LocalTime workStartTime,
        LocalTime workEndTime,
        boolean restDay,
        boolean late,
        boolean early,
        String status
    ) {
        this(
            id,
            userId,
            attendanceDate,
            clockInTime,
            clockOutTime,
            workStartTime,
            workEndTime,
            restDay,
            late,
            early,
            status,
            0,
            false,
            false,
            null,
            null,
            "RECORDED_RULE"
        );
    }
}
