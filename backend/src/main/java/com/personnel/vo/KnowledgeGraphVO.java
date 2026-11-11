package com.personnel.vo;

import java.time.LocalDate;
import java.util.List;

public record KnowledgeGraphVO(
    Long id,
    String version,
    String title,
    String status,
    String contentHash,
    List<Source> sources,
    List<Node> nodes,
    List<Edge> edges,
    List<Rule> rules
) {
    public record Source(
        String id,
        String title,
        String author,
        String url,
        LocalDate accessedDate,
        String license,
        String attribution,
        String contentVersion,
        String evidenceLevel
    ) {}

    public record Node(String id, String type, String label, String description, String sourceId) {}

    public record Edge(
        String id,
        String from,
        String to,
        String relation,
        String explanation,
        String sourceId
    ) {}

    public record Condition(String nodeId, String dimension, String pole, Integer minimumPercent) {}

    public record Rule(
        String id,
        String actionId,
        String contextId,
        String explanation,
        List<Condition> conditions,
        List<String> edgeIds
    ) {}
}
