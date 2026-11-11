package com.personnel.vo;

import java.math.BigDecimal;

public record AssessmentDimensionVO(
    String dimension,
    int rawFirstScore,
    int minimumScore,
    int maximumScore,
    BigDecimal firstPercent,
    BigDecimal secondPercent,
    String preferredPole,
    boolean balanced,
    boolean weakPreference
) {}
