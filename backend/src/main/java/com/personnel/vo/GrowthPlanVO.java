package com.personnel.vo;

import java.util.List;

/** Advisory plan frozen with the report, not a performance task. */
public record GrowthPlanVO(
    String id,
    String context,
    String action,
    String suggestedCycle,
    String observationTarget,
    String reviewPrompt,
    String basis,
    String ruleId,
    String graphVersion,
    List<String> sourceIds,
    String evidenceLevel,
    String templateVersion,
    String templateContentHash
) {}
