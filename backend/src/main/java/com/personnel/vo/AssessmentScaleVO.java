package com.personnel.vo;

import java.time.LocalDateTime;

public record AssessmentScaleVO(
    Long id,
    String status,
    String contentHash,
    ScaleDefinitionVO definition,
    Long publishedBy,
    LocalDateTime publishedAt
) {}
