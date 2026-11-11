package com.personnel.vo;

import java.util.List;

public record JobExplorationVO(
    Long resultId,
    String scaleVersion,
    String graphVersion,
    String notice,
    List<Candidate> positions
) {
    public record Candidate(
        JobProfileVO profile,
        boolean comparable,
        Integer ruleCoveragePercent,
        List<String> matchedRuleIds,
        String explanation
    ) {}
}
