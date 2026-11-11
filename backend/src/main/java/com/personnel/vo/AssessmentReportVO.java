package com.personnel.vo;

import java.util.List;

public record AssessmentReportVO(
    String reportVersion,
    String disclaimer,
    List<String> preferenceSummary,
    List<String> strengths,
    List<String> developmentDirections,
    List<String> workPreferences,
    List<String> careerSuggestions,
    List<String> growthSuggestions,
    String sourceBasis,
    String limitations,
    GraphExplanationVO graphExplanation,
    List<GrowthPlanVO> growthPlans
) {}
