package com.personnel.vo;

import java.time.LocalDateTime;

public record AssessmentHistoryVO(
    Long id,
    String scaleName,
    String scaleVersion,
    String scoringVersion,
    String contentHash,
    String typeCode,
    LocalDateTime submittedAt
) {}
