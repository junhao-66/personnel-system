package com.personnel.common.constant;

import java.util.List;

public final class AssessmentConstants {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String ARCHIVED = "ARCHIVED";
    public static final String COMPLETED = "COMPLETED";
    public static final String SCORING_VERSION = "WEIGHTED_BIPOLAR_1_5_V1";
    public static final String OEJTS_SCORING_VERSION = "OEJTS_OPENJUNG_32_V1";
    public static final String REPORT_VERSION = "GRAPH_GROWTH_PLAN_V3";
    public static final List<String> DIMENSIONS = List.of("EI", "SN", "TF", "JP");
    public static final String DISCLAIMER =
        "非官方 MBTI 测评。结果仅表示本次自陈的人格偏好倾向，不代表能力、绩效、心理诊断或发展潜力，不用于招聘淘汰、晋升降级或绩效处罚。";

    private AssessmentConstants() {}
}
