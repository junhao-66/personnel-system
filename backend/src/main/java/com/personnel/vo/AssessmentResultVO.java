package com.personnel.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AssessmentResultVO(
    Long id,
    Long userId,
    String scaleName,
    String scaleVersion,
    String scoringVersion,
    String contentHash,
    String typeCode,
    LocalDateTime submittedAt,
    List<AssessmentDimensionVO> dimensions,
    AssessmentReportVO report,
    ScaleDefinitionVO scale
) {}
