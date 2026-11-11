package com.personnel.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record TodayAttendanceVO(
    LocalDate attendanceDate,
    LocalDateTime serverTime,
    LocalTime workStartTime,
    LocalTime workEndTime,
    String timeZone,
    boolean restDay,
    AttendanceRecordVO record,
    AttendanceRecordVO dayStatus
) {}
