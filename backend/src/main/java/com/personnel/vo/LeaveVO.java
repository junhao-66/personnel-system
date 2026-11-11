package com.personnel.vo;

import java.time.LocalDateTime;

public record LeaveVO(
    Long id,
    Long userId,
    String realName,
    Long departmentId,
    String leaveType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String reason,
    String status,
    Long reviewerId,
    String reviewComment,
    LocalDateTime reviewedAt,
    LocalDateTime createTime
) {}
