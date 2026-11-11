package com.personnel.vo;

import java.time.LocalDateTime;
import java.util.Map;

public record AssessmentSessionVO(
    Long id,
    String status,
    int revision,
    String contentHash,
    ScaleDefinitionVO scale,
    Map<String, Integer> answers,
    LocalDateTime consentAt,
    LocalDateTime updatedAt,
    LocalDateTime submittedAt
) {}
