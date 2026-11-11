package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.AssessmentJson;
import com.personnel.convert.JobPreferenceConvert;
import com.personnel.dto.req.AssessmentQueryReqDTO;
import com.personnel.dto.req.JobProfileCreateReqDTO;
import com.personnel.entity.JobPreferenceProfileDO;
import com.personnel.mapper.JobPreferenceProfileMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobPreferenceServiceImpl implements JobPreferenceService {

    private static final String NOTICE =
        "探索性岗位偏好提示：规则及权重由项目维护者整理，未经岗位效度验证。覆盖率仅表示演示规则的加权命中，不代表岗位适配、能力、绩效或录用建议；请结合兴趣、技能、经历与实际任务自愿探索。";
    private final JobPreferenceProfileMapper mapper;
    private final UserService users;
    private final OrganizationService organizations;
    private final KnowledgeGraphService graphs;
    private final AssessmentService assessments;
    private final OperationAuditService audit;
    private final AssessmentJson json;
    private final Validator validator;
    private final Clock clock;
    private final JobProfileCatalogService catalog;

    public JobPreferenceServiceImpl(
        JobPreferenceProfileMapper mapper,
        UserService users,
        OrganizationService organizations,
        KnowledgeGraphService graphs,
        AssessmentService assessments,
        OperationAuditService audit,
        AssessmentJson json,
        Validator validator,
        Clock clock,
        JobProfileCatalogService catalog
    ) {
        this.mapper = mapper;
        this.users = users;
        this.organizations = organizations;
        this.graphs = graphs;
        this.assessments = assessments;
        this.audit = audit;
        this.json = json;
        this.validator = validator;
        this.clock = clock;
        this.catalog = catalog;
    }

    private UserVO requireEnabled(Long actor) {
        var user = users.getById(actor);
        if (
            !"APPROVED".equals(user.auditStatus()) || !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("账号未审核通过或已停用");
        return user;
    }

    private void requireAdmin(Long actor) {
        if (!"ADMIN".equals(requireEnabled(actor).roleCode())) throw new BusinessException(
            "仅管理员可维护岗位探索配置"
        );
    }

    @Override
    public List<JobProfileVO> listProfiles(Long actor) {
        requireAdmin(actor);
        return mapper
            .selectList(
                Wrappers.<JobPreferenceProfileDO>lambdaQuery().orderByDesc(
                    JobPreferenceProfileDO::getId
                )
            )
            .stream()
            .map(r -> JobPreferenceConvert.toVO(r, json))
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobProfileVO createProfile(Long actor, JobProfileCreateReqDTO request) {
        requireAdmin(actor);
        if (request == null || !validator.validate(request).isEmpty()) throw new BusinessException(
            "请完整填写有效的岗位探索配置"
        );
        var position = organizations
            .listPositions(null)
            .stream()
            .filter(
                p -> p.id().equals(request.positionId()) && Integer.valueOf(1).equals(p.isEnabled())
            )
            .findFirst()
            .orElseThrow(() -> new BusinessException("请选择启用中的岗位"));
        organizations.validateAssignment(position.departmentId(), position.id());
        var graph = graphs.getCurrent();
        if (graph == null || !graph.id().equals(request.graphId())) throw new BusinessException(
            "已发布图谱已变化，请刷新后重新选择规则"
        );
        var ruleIds = new HashSet<String>();
        int total = 0;
        var weighted = new ArrayList<JobProfileVO.WeightedRule>();
        var nodes = graph
            .nodes()
            .stream()
            .collect(Collectors.toMap(KnowledgeGraphVO.Node::id, n -> n));
        var edges = graph
            .edges()
            .stream()
            .collect(Collectors.toMap(KnowledgeGraphVO.Edge::id, e -> e));
        for (var selection : request.rules()) {
            if (!ruleIds.add(selection.ruleId())) throw new BusinessException(
                "同一岗位配置不能重复选择规则"
            );
            var rule = graph
                .rules()
                .stream()
                .filter(r -> r.id().equals(selection.ruleId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("所选多维规则不存在"));
            var selectedEdges = rule.edgeIds().stream().map(edges::get).toList();
            var nodeIds = new LinkedHashSet<String>();
            selectedEdges.forEach(e -> {
                nodeIds.add(e.from());
                nodeIds.add(e.to());
            });
            var selectedNodes = nodeIds.stream().map(nodes::get).toList();
            var sources = new LinkedHashSet<String>();
            selectedNodes.forEach(n -> sources.add(n.sourceId()));
            selectedEdges.forEach(e -> sources.add(e.sourceId()));
            weighted.add(
                new JobProfileVO.WeightedRule(
                    selection.weight(),
                    rule,
                    nodes.get(rule.contextId()).label(),
                    nodes.get(rule.actionId()).description(),
                    selectedNodes,
                    selectedEdges,
                    graph
                        .sources()
                        .stream()
                        .filter(s -> sources.contains(s.id()))
                        .toList()
                )
            );
            total += selection.weight();
        }
        if (total != 100) throw new BusinessException("规则权重合计必须为100");
        var definition = new JobProfileVO.Definition(
            position.id(),
            position.positionName(),
            request.version(),
            request.title().trim(),
            graph.version(),
            graph.contentHash(),
            request.attribution().trim(),
            weighted
        );
        String serialized = json.write(definition),
            hash;
        try {
            hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    serialized.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        var row = JobPreferenceConvert.toDO(
            definition,
            serialized,
            hash,
            actor,
            LocalDateTime.now(clock)
        );
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("岗位配置版本号已存在，请使用新版本号");
        }
        audit.append(
            AuditOperation.JOB_PROFILE_CREATE,
            row.getId(),
            List.of("version", "positionId", "definitionJson", "status")
        );
        return JobPreferenceConvert.toVO(row, json);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-jobs",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void publishProfile(Long actor, Long id) {
        requireAdmin(actor);
        var row = mapper.selectById(id);
        if (row == null || !"DRAFT".equals(row.getStatus())) throw new BusinessException(
            "仅草稿岗位配置可发布"
        );
        var definition = JobPreferenceConvert.toVO(row, json).definition();
        var graph = graphs.getCurrent();
        if (
            graph == null ||
            !graph.version().equals(definition.graphVersion()) ||
            !graph.contentHash().equals(definition.graphContentHash())
        ) throw new BusinessException("当前图谱与配置依据已不同，请基于最新图谱另建配置");
        var position = organizations
            .listPositions(null)
            .stream()
            .filter(p -> p.id().equals(row.getPositionId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException("岗位不存在"));
        organizations.validateAssignment(position.departmentId(), position.id());
        if (
            mapper.update(
                null,
                Wrappers.<JobPreferenceProfileDO>lambdaUpdate()
                    .eq(JobPreferenceProfileDO::getId, id)
                    .eq(JobPreferenceProfileDO::getStatus, "DRAFT")
                    .set(JobPreferenceProfileDO::getStatus, "PUBLISHED")
            ) !=
            1
        ) throw new BusinessException("配置状态已变化，请刷新");
        audit.append(AuditOperation.JOB_PROFILE_PUBLISH, id, List.of("status"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-jobs",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void archiveProfile(Long actor, Long id) {
        requireAdmin(actor);
        if (
            mapper.update(
                null,
                Wrappers.<JobPreferenceProfileDO>lambdaUpdate()
                    .eq(JobPreferenceProfileDO::getId, id)
                    .eq(JobPreferenceProfileDO::getStatus, "PUBLISHED")
                    .set(JobPreferenceProfileDO::getStatus, "ARCHIVED")
            ) !=
            1
        ) throw new BusinessException("仅已发布岗位配置可停用");
        audit.append(AuditOperation.JOB_PROFILE_ARCHIVE, id, List.of("status"));
    }

    @Override
    public JobExplorationVO explore(Long actor, Long owner) {
        var user = requireEnabled(actor);
        if (!actor.equals(owner) && !"ADMIN".equals(user.roleCode())) throw new BusinessException(
            "无权查看他人岗位探索结果"
        );
        var history = assessments.listHistory(actor, owner, new AssessmentQueryReqDTO());
        if (history.records().isEmpty()) return new JobExplorationVO(
            null,
            null,
            null,
            NOTICE + " 请先完成一次测评。",
            List.of()
        );
        var result = assessments.getResult(actor, history.records().get(0).id());
        var evidence = result.report().graphExplanation();
        var matched = evidence == null
            ? Set.<String>of()
            : evidence
                .matches()
                .stream()
                .map(GraphExplanationVO.Match::ruleId)
                .collect(Collectors.toSet());
        var departments = organizations
            .listDepartments()
            .stream()
            .filter(d -> Integer.valueOf(1).equals(d.isEnabled()))
            .map(DepartmentVO::id)
            .collect(Collectors.toSet());
        var positions = organizations
            .listPositions(null)
            .stream()
            .filter(
                p ->
                    Integer.valueOf(1).equals(p.isEnabled()) &&
                    (p.departmentId() == null || departments.contains(p.departmentId()))
            )
            .map(PositionVO::id)
            .collect(Collectors.toSet());
        var effective = new LinkedHashMap<Long, JobProfileVO>();
        catalog
            .published()
            .forEach(profile -> {
                if (positions.contains(profile.definition().positionId())) effective.putIfAbsent(
                    profile.definition().positionId(),
                    profile
                );
            });
        var candidates = effective
            .values()
            .stream()
            .map(profile -> {
                var definition = profile.definition();
                boolean comparable =
                    evidence != null &&
                    Objects.equals(evidence.graphVersion(), definition.graphVersion()) &&
                    Objects.equals(evidence.contentHash(), definition.graphContentHash());
                var hits = comparable
                    ? definition
                        .rules()
                        .stream()
                        .filter(r -> matched.contains(r.rule().id()))
                        .map(r -> r.rule().id())
                        .toList()
                    : List.<String>of();
                Integer coverage = comparable
                    ? definition
                        .rules()
                        .stream()
                        .filter(r -> hits.contains(r.rule().id()))
                        .mapToInt(JobProfileVO.WeightedRule::weight)
                        .sum()
                    : null;
                return new JobExplorationVO.Candidate(
                    profile,
                    comparable,
                    coverage,
                    hits,
                    !comparable
                        ? "当次报告与岗位配置的图谱依据版本不同，未计算覆盖率；历史报告不会补算。"
                        : hits.isEmpty()
                            ? "当次快照未命中这些多维规则，不据此判断适合或不适合该岗位。"
                            : "可结合以下命中的情境与行动自愿探索；覆盖率不是岗位适配度。"
                );
            })
            .sorted(Comparator.comparing(c -> c.profile().definition().positionName()))
            .toList();
        return new JobExplorationVO(
            result.id(),
            result.scaleVersion(),
            evidence == null ? null : evidence.graphVersion(),
            NOTICE,
            candidates
        );
    }
}
