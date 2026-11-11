package com.personnel.vo;

import java.util.List;

/** Frozen graph evidence, saved as part of each assessment report. */
public record GraphExplanationVO(
    String graphVersion,
    String contentHash,
    String notice,
    List<Match> matches
) {
    public record Match(
        String ruleId,
        String context,
        String action,
        String explanation,
        List<String> matchedConditions,
        List<KnowledgeGraphVO.Node> nodes,
        List<KnowledgeGraphVO.Edge> edges,
        List<KnowledgeGraphVO.Source> sources
    ) {}
}
