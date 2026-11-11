package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.AssessmentJson;
import com.personnel.convert.KnowledgeGraphConvert;
import com.personnel.dto.req.GraphImportReqDTO;
import com.personnel.entity.KnowledgeGraphDO;
import com.personnel.mapper.KnowledgeGraphMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import jakarta.validation.Validator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned adjacency graph. Multi-premise AND rules return bounded, source-traceable evidence paths. */
@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
        KnowledgeGraphServiceImpl.class
    );
    private final KnowledgeGraphMapper mapper;
    private final AssessmentJson json;
    private final UserService users;
    private final Validator validator;
    private final Clock clock;
    private final OperationAuditService audit;

    public KnowledgeGraphServiceImpl(
        KnowledgeGraphMapper mapper,
        AssessmentJson json,
        UserService users,
        Validator validator,
        Clock clock,
        OperationAuditService audit
    ) {
        this.audit = audit;
        this.mapper = mapper;
        this.json = json;
        this.users = users;
        this.validator = validator;
        this.clock = clock;
    }

    private void requireAdmin(Long id) {
        var user = users.getById(id);
        if (
            !"ADMIN".equals(user.roleCode()) ||
            !"APPROVED".equals(user.auditStatus()) ||
            !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("仅有效管理员可维护知识图谱");
    }

    private KnowledgeGraphVO toVO(KnowledgeGraphDO value) {
        return KnowledgeGraphConvert.toVO(
            value,
            json.read(value.getDefinitionJson(), KnowledgeGraphVO.class)
        );
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(
        cacheNames = "catalog-graph",
        key = "@staticCachePolicy.key('current')",
        condition = "@staticCachePolicy.available()"
    )
    public KnowledgeGraphVO getCurrent() {
        var values = mapper.selectList(
            Wrappers.<KnowledgeGraphDO>lambdaQuery()
                .eq(KnowledgeGraphDO::getStatus, "PUBLISHED")
                .orderByDesc(KnowledgeGraphDO::getId)
                .last("LIMIT 1")
        );
        return values.isEmpty() ? null : toVO(values.get(0));
    }

    @Override
    public List<KnowledgeGraphVO> listVersions(Long adminId) {
        requireAdmin(adminId);
        return mapper
            .selectList(
                Wrappers.<KnowledgeGraphDO>lambdaQuery().orderByDesc(KnowledgeGraphDO::getId)
            )
            .stream()
            .map(this::toVO)
            .toList();
    }

    private <T> Map<String, T> indexed(List<T> values, Function<T, String> key) {
        var result = new LinkedHashMap<String, T>();
        for (var value : values)
            if (result.put(key.apply(value), value) != null) throw new BusinessException(
                "图谱标识不能重复"
            );
        return result;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(message);
    }

    private boolean reaches(String start, String target, List<GraphImportReqDTO.Edge> edges) {
        var seen = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            var current = queue.remove();
            if (current.equals(target)) return true;
            if (seen.add(current)) edges
                .stream()
                .filter(e -> e.from().equals(current))
                .forEach(e -> queue.add(e.to()));
        }
        return false;
    }

    private void validate(GraphImportReqDTO request) {
        require(validator.validate(request).isEmpty(), "图谱字段不完整或超出数量限制");
        var sources = indexed(request.sources(), GraphImportReqDTO.Source::id);
        var nodes = indexed(request.nodes(), GraphImportReqDTO.Node::id);
        var edges = indexed(request.edges(), GraphImportReqDTO.Edge::id);
        indexed(request.rules(), GraphImportReqDTO.Rule::id);
        for (var source : request.sources()) {
            URI uri;
            try {
                uri = URI.create(source.url());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("来源链接格式不正确");
            }
            require(
                List.of("http", "https").contains(uri.getScheme()) &&
                uri.getHost() != null &&
                uri.getUserInfo() == null,
                "来源链接必须是HTTP/HTTPS"
            );
        }
        for (var node : request.nodes())
            require(sources.containsKey(node.sourceId()), "节点缺少有效来源");
        for (var edge : request.edges())
            require(
                nodes.containsKey(edge.from()) &&
                nodes.containsKey(edge.to()) &&
                sources.containsKey(edge.sourceId()),
                "关系存在悬空节点或来源"
            );
        for (var rule : request.rules()) {
            require(
                nodes.containsKey(rule.actionId()) &&
                "ACTION".equals(nodes.get(rule.actionId()).type()),
                "规则必须指向行动节点"
            );
            require(
                nodes.containsKey(rule.contextId()) &&
                "CONTEXT".equals(nodes.get(rule.contextId()).type()),
                "规则必须注明适用情境"
            );
            require(
                new HashSet<>(rule.edgeIds()).size() == rule.edgeIds().size() &&
                edges.keySet().containsAll(rule.edgeIds()),
                "规则关系不存在或重复"
            );
            var path = rule.edgeIds().stream().map(edges::get).toList();
            var dimensions = new HashSet<String>();
            for (var condition : rule.conditions()) {
                require(dimensions.add(condition.dimension()), "规则前提必须来自不同维度");
                require(
                    condition.dimension().contains(condition.pole()) &&
                    condition.pole().equals(condition.nodeId()) &&
                    nodes.containsKey(condition.nodeId()) &&
                    "PREFERENCE".equals(nodes.get(condition.nodeId()).type()),
                    "偏好前提与节点不一致"
                );
                require(
                    reaches(condition.nodeId(), rule.actionId(), path),
                    "每个偏好前提都需要到建议的有向依据路径"
                );
            }
            require(reaches(rule.contextId(), rule.actionId(), path), "情境缺少到建议的依据路径");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeGraphVO importVersion(Long adminId, GraphImportReqDTO request) {
        requireAdmin(adminId);
        validate(request);
        var definition = KnowledgeGraphConvert.toDefinition(request);
        var content = json.write(definition);
        String hash;
        try {
            hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    content.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        var value = KnowledgeGraphConvert.toDO(
            adminId,
            definition,
            content,
            hash,
            LocalDateTime.now(clock)
        );
        try {
            mapper.insert(value);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("图谱版本已存在，请使用新版本号");
        }
        audit.append(
            AuditOperation.GRAPH_IMPORT,
            value.getId(),
            List.of("definitionJson", "status")
        );
        log.info("Knowledge graph imported id:{} adminId:{}", value.getId(), adminId);
        return toVO(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-graph",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void publishVersion(Long adminId, Long id) {
        requireAdmin(adminId);
        require(
            mapper.update(
                null,
                Wrappers.<KnowledgeGraphDO>lambdaUpdate()
                    .eq(KnowledgeGraphDO::getId, id)
                    .eq(KnowledgeGraphDO::getStatus, "DRAFT")
                    .set(KnowledgeGraphDO::getStatus, "PUBLISHED")
            ) ==
            1,
            "仅草稿版本可以发布"
        );
        audit.append(AuditOperation.GRAPH_PUBLISH, id, List.of("status"));
        log.info("Knowledge graph published id:{} adminId:{}", id, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-graph",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void archiveVersion(Long adminId, Long id) {
        requireAdmin(adminId);
        require(
            mapper.update(
                null,
                Wrappers.<KnowledgeGraphDO>lambdaUpdate()
                    .eq(KnowledgeGraphDO::getId, id)
                    .eq(KnowledgeGraphDO::getStatus, "PUBLISHED")
                    .set(KnowledgeGraphDO::getStatus, "ARCHIVED")
            ) ==
            1,
            "仅已发布版本可以停用"
        );
        audit.append(AuditOperation.GRAPH_ARCHIVE, id, List.of("status"));
        log.info("Knowledge graph archived id:{} adminId:{}", id, adminId);
    }

    @Override
    public GraphExplanationVO explain(List<AssessmentDimensionVO> dimensions) {
        var graph = getCurrent();
        if (graph == null) return new GraphExplanationVO(
            null,
            null,
            "尚无已发布图谱，保留基础报告；未生成图谱建议。",
            List.of()
        );
        var scores = dimensions
            .stream()
            .collect(Collectors.toMap(AssessmentDimensionVO::dimension, Function.identity()));
        var nodes = indexed(graph.nodes(), KnowledgeGraphVO.Node::id);
        var edges = indexed(graph.edges(), KnowledgeGraphVO.Edge::id);
        var sources = indexed(graph.sources(), KnowledgeGraphVO.Source::id);
        var matches = new ArrayList<GraphExplanationVO.Match>();
        for (var rule : graph.rules()) {
            boolean matched = rule
                .conditions()
                .stream()
                .allMatch(c -> meetsThreshold(scores.get(c.dimension()), c));
            if (!matched) continue;
            var path = rule.edgeIds().stream().map(edges::get).toList();
            var nodeIds = new LinkedHashSet<String>();
            path.forEach(e -> {
                nodeIds.add(e.from());
                nodeIds.add(e.to());
            });
            var pathNodes = nodeIds.stream().map(nodes::get).toList();
            var sourceIds = new LinkedHashSet<String>();
            path.forEach(e -> sourceIds.add(e.sourceId()));
            pathNodes.forEach(n -> sourceIds.add(n.sourceId()));
            var conditions = rule
                .conditions()
                .stream()
                .map(c -> {
                    var d = scores.get(c.dimension());
                    var p = c.pole().equals(c.dimension().substring(0, 1))
                        ? d.firstPercent()
                        : d.secondPercent();
                    return c.pole() + "端 " + p + "% ≥ " + c.minimumPercent() + "%";
                })
                .toList();
            matches.add(
                new GraphExplanationVO.Match(
                    rule.id(),
                    nodes.get(rule.contextId()).label(),
                    nodes.get(rule.actionId()).description(),
                    rule.explanation(),
                    conditions,
                    pathNodes,
                    path,
                    sourceIds.stream().map(sources::get).toList()
                )
            );
        }
        return new GraphExplanationVO(
            graph.version(),
            graph.contentHash(),
            "多维前提同时满足才展示候选建议；情境为适用条件而非已知个人事实。来源标记PROJECT_DEMO的关系是项目演示假设，不是科学因果或岗位结论。",
            matches
        );
    }

    private boolean meetsThreshold(
        AssessmentDimensionVO value,
        KnowledgeGraphVO.Condition condition
    ) {
        if (value == null || value.weakPreference() || value.balanced()) return false;
        long range = value.maximumScore() - value.minimumScore();
        long offset = value.rawFirstScore() - value.minimumScore();
        if (!condition.pole().equals(condition.dimension().substring(0, 1))) offset =
            range - offset;
        // Use exact integer scores: display rounding must not make a below-threshold score qualify.
        return range > 0 && offset * 100L >= condition.minimumPercent() * range;
    }
}
