package com.personnel.vo;

import java.util.List;

public record JobProfileVO(Long id, String status, String contentHash, Definition definition) {
    public record Definition(
        Long positionId,
        String positionName,
        String version,
        String title,
        String graphVersion,
        String graphContentHash,
        String attribution,
        List<WeightedRule> rules
    ) {}

    public record WeightedRule(
        Integer weight,
        KnowledgeGraphVO.Rule rule,
        String context,
        String action,
        List<KnowledgeGraphVO.Node> nodes,
        List<KnowledgeGraphVO.Edge> edges,
        List<KnowledgeGraphVO.Source> sources
    ) {}
}
