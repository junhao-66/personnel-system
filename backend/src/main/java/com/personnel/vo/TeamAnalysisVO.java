package com.personnel.vo;

import java.util.List;

public record TeamAnalysisVO(
    Long departmentId,
    String departmentName,
    TalentDashboardVO distribution,
    List<DimensionCount> dimensions,
    List<String> collaborationTips,
    String ruleVersion,
    String notice
) {
    public record DimensionCount(String dimension, long firstCount, long secondCount) {}
}
