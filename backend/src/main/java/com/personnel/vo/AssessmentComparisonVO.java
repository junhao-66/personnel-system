package com.personnel.vo;

public record AssessmentComparisonVO(
    boolean comparable,
    String explanation,
    AssessmentResultVO first,
    AssessmentResultVO second
) {}
