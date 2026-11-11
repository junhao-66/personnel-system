package com.personnel.convert;

import com.personnel.dto.req.GraphImportReqDTO;
import com.personnel.entity.KnowledgeGraphDO;
import com.personnel.vo.KnowledgeGraphVO;
import java.time.LocalDateTime;

public final class KnowledgeGraphConvert {

    private KnowledgeGraphConvert() {}

    public static KnowledgeGraphVO toDefinition(GraphImportReqDTO value) {
        return new KnowledgeGraphVO(
            null,
            value.version(),
            value.title(),
            "DRAFT",
            null,
            value
                .sources()
                .stream()
                .map(s ->
                    new KnowledgeGraphVO.Source(
                        s.id(),
                        s.title(),
                        s.author(),
                        s.url(),
                        s.accessedDate(),
                        s.license(),
                        s.attribution(),
                        s.contentVersion(),
                        s.evidenceLevel()
                    )
                )
                .toList(),
            value
                .nodes()
                .stream()
                .map(n ->
                    new KnowledgeGraphVO.Node(
                        n.id(),
                        n.type(),
                        n.label(),
                        n.description(),
                        n.sourceId()
                    )
                )
                .toList(),
            value
                .edges()
                .stream()
                .map(e ->
                    new KnowledgeGraphVO.Edge(
                        e.id(),
                        e.from(),
                        e.to(),
                        e.relation(),
                        e.explanation(),
                        e.sourceId()
                    )
                )
                .toList(),
            value
                .rules()
                .stream()
                .map(r ->
                    new KnowledgeGraphVO.Rule(
                        r.id(),
                        r.actionId(),
                        r.contextId(),
                        r.explanation(),
                        r
                            .conditions()
                            .stream()
                            .map(c ->
                                new KnowledgeGraphVO.Condition(
                                    c.nodeId(),
                                    c.dimension(),
                                    c.pole(),
                                    c.minimumPercent()
                                )
                            )
                            .toList(),
                        r.edgeIds()
                    )
                )
                .toList()
        );
    }

    public static KnowledgeGraphDO toDO(
        Long actor,
        KnowledgeGraphVO value,
        String json,
        String hash,
        LocalDateTime now
    ) {
        var result = new KnowledgeGraphDO();
        result.setVersion(value.version());
        result.setTitle(value.title());
        result.setStatus("DRAFT");
        result.setDefinitionJson(json);
        result.setContentHash(hash);
        result.setCreateBy(actor);
        result.setCreateTime(now);
        return result;
    }

    public static KnowledgeGraphVO toVO(KnowledgeGraphDO value, KnowledgeGraphVO definition) {
        return new KnowledgeGraphVO(
            value.getId(),
            value.getVersion(),
            value.getTitle(),
            value.getStatus(),
            value.getContentHash(),
            definition.sources(),
            definition.nodes(),
            definition.edges(),
            definition.rules()
        );
    }
}
