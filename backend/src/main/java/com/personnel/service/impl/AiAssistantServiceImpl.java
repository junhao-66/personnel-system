package com.personnel.service.impl;

import static com.personnel.common.ai.GroundedAiContext.*;

import com.personnel.common.ai.*;
import com.personnel.common.exception.BusinessException;
import com.personnel.config.AiProperties;
import com.personnel.dto.req.AssessmentQueryReqDTO;
import com.personnel.service.*;
import com.personnel.vo.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final String NOTICE =
        "增强内容独立于原始报告，不改写得分和历史快照。MOCK是本地演示，RULE_FALLBACK是规则降级；REMOTE仅由模型选择已有依据与表达框架，正文由服务端依据模板展开，不展示模型任意生成的事实。仅供自愿反思，不能判断能力、绩效、诊断或录用。无对话存储，不发送姓名、联系方式、用户ID、答卷或问题原文。";
    private static final Pattern UNSUPPORTED = Pattern.compile(
        "能力|绩效|淘汰|辞退|解雇|诊断|风险|最优|最适合|晋升|升职|降职|差劲|谁最|招聘|录用|潜力|心理疾病|身份证|电话|手机号|邮箱|工资|薪资|住址|password|salary|diagnos|hire|fire|worst|best",
        Pattern.CASE_INSENSITIVE
    );
    private final UserService users;
    private final AssessmentService assessments;
    private final TeamAnalysisService teams;
    private final StaticReferenceService references;
    private final AiProperties properties;
    private final RemoteGroundingClient remote;

    public AiAssistantServiceImpl(
        UserService users,
        AssessmentService assessments,
        TeamAnalysisService teams,
        StaticReferenceService references,
        AiProperties properties,
        RemoteGroundingClient remote
    ) {
        this.users = users;
        this.assessments = assessments;
        this.teams = teams;
        this.references = references;
        this.properties = properties;
        this.remote = remote;
    }

    private UserVO enabled(Long actor) {
        var user = users.getById(actor);
        if (
            !"APPROVED".equals(user.auditStatus()) || !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("账号未审核通过或已停用");
        return user;
    }

    private void admin(Long actor) {
        if (!"ADMIN".equals(enabled(actor).roleCode())) throw new BusinessException(
            "仅管理员可使用人才档案及部门问答"
        );
    }

    @Override
    public AiStatusVO status(Long actor) {
        enabled(actor);
        return new AiStatusVO(
            properties.getMode(),
            properties.isExternalEnabled(),
            remote.configured(),
            NOTICE
        );
    }

    private List<Evidence> reportEvidence(AssessmentResultVO result) {
        var values = new ArrayList<Evidence>();
        values.add(
            new Evidence(
                "result",
                "FACT",
                "本次类型编码为" +
                result.typeCode() +
                "；量表版本" +
                result.scaleVersion() +
                "，仅表示当次偏好记录。",
                "冻结报告 / " + result.report().reportVersion()
            )
        );
        for (var d : result.dimensions())
            values.add(
                new Evidence(
                    "dimension-" + d.dimension(),
                    "FACT",
                    d.dimension() +
                    "两端刻度为" +
                    d.firstPercent() +
                    "% / " +
                    d.secondPercent() +
                    "%；" +
                    (d.balanced()
                            ? "均衡，字母按约定编码"
                            : d.weakPreference()
                                ? "倾向较弱"
                                : "本次偏好端为" + d.preferredPole()) +
                    "，不表示能力。",
                    "冻结四维得分 / " + result.scoringVersion()
                )
            );
        var graph = result.report().graphExplanation();
        if (graph != null) for (var match : graph.matches())
            values.add(
                new Evidence(
                    "rule-" + match.ruleId(),
                    "ACTION",
                    "如果处于“" +
                    match.context() +
                    "”情境，可自愿尝试：" +
                    match.action() +
                    "。命中条件：" +
                    String.join(" 且 ", match.matchedConditions()),
                    "冻结图谱 " +
                    graph.graphVersion() +
                    " / " +
                    match.ruleId() +
                    " / 来源 " +
                    String.join(
                        "、",
                        match
                            .sources()
                            .stream()
                            .map(s -> s.title() + " [" + s.evidenceLevel() + "]")
                            .toList()
                    )
                )
            );
        if (result.report().growthPlans() != null) for (var plan : result.report().growthPlans())
            if (values.size() < 24) values.add(
                new Evidence(
                    "growth-" + values.size(),
                    "ACTION",
                    plan.context() +
                    "：" +
                    plan.action() +
                    "；" +
                    plan.suggestedCycle() +
                    "；观察：" +
                    plan.observationTarget() +
                    "；复盘：" +
                    plan.reviewPrompt(),
                    "冻结成长计划 / " +
                    (plan.templateVersion() == null ? "旧版模板" : plan.templateVersion())
                )
            );
        return values.stream().limit(24).toList();
    }

    @Override
    public AiAnswerVO expandReport(Long actor, Long resultId) {
        enabled(actor);
        var result = assessments.getResult(actor, resultId);
        return answer(
            "EXPAND",
            reportEvidence(result),
            result.scaleVersion() + " / " + result.report().reportVersion(),
            result.id()
        );
    }

    private String intent(String question, boolean department) {
        if (
            question == null || question.isBlank() || question.length() > 500
        ) throw new BusinessException("请输入1至500字的问题");
        if (UNSUPPORTED.matcher(question).find()) return "UNSUPPORTED";
        if (
            Pattern.compile("成长|发展|行动|建议|复盘|培养|growth", Pattern.CASE_INSENSITIVE)
                .matcher(question)
                .find()
        ) return "GROWTH";
        if (
            Pattern.compile("沟通|协作|合作|团队|collabor", Pattern.CASE_INSENSITIVE)
                .matcher(question)
                .find()
        ) return "COLLABORATION";
        if (
            Pattern.compile(
                "测评|类型|维度|偏好|分布|完成|人数|多少|mbti|assessment",
                Pattern.CASE_INSENSITIVE
            )
                .matcher(question)
                .find()
        ) return department ? "TEAM_COUNTS" : "ASSESSMENT";
        if (
            !department &&
            Pattern.compile("档案|角色|岗位|部门|profile", Pattern.CASE_INSENSITIVE)
                .matcher(question)
                .find()
        ) return "PROFILE";
        return "UNSUPPORTED";
    }

    private AiAnswerVO refused(String reason) {
        return new AiAnswerVO(
            "REFUSED",
            reason,
            references.prompts().get("version"),
            "无可回答的结构化依据",
            null,
            List.of(
                new AiAnswerVO.Paragraph(
                    "scope",
                    "当前只能整理已保存的档案角色、测评偏好、成长行动或部门人数与协作提示。请围绕这些数据提问；不能判断能力、绩效、录用、风险，也不提供联系方式等敏感字段。",
                    "系统回答范围"
                )
            ),
            NOTICE
        );
    }

    @Override
    public AiAnswerVO askEmployee(Long actor, Long owner, String question) {
        admin(actor);
        var target = users.getById(owner);
        String intent = intent(question, false);
        if ("UNSUPPORTED".equals(intent)) return refused("OUT_OF_SCOPE");
        var facts = new ArrayList<Evidence>();
        facts.add(
            new Evidence(
                "profile",
                "FACT",
                "当前档案角色为" +
                references.dictionary().getOrDefault(target.roleCode(), "未知角色") +
                "；" +
                (target.departmentId() == null ? "未分配部门" : "已分配部门") +
                "；" +
                (target.positionId() == null ? "未分配岗位" : "已分配岗位") +
                "。",
                "当前用户档案，不含身份与联系方式"
            )
        );
        Long resultId = null;
        String version = "PROFILE_CURRENT";
        if (!"PROFILE".equals(intent)) {
            var history = assessments.listHistory(actor, owner, new AssessmentQueryReqDTO());
            if (history.records().isEmpty()) facts.add(
                new Evidence(
                    "no-result",
                    "FACT",
                    "该档案暂无已提交的测评报告，不能生成个人偏好或成长结论。",
                    "测评历史查询"
                )
            );
            else {
                var result = assessments.getResult(actor, history.records().get(0).id());
                facts.addAll(reportEvidence(result));
                resultId = result.id();
                version = result.scaleVersion() + " / " + result.report().reportVersion();
            }
        }
        return answer(intent, facts, version, resultId);
    }

    @Override
    public AiAnswerVO askDepartment(Long actor, Long departmentId, String question) {
        admin(actor);
        var data = teams.analyzeDepartment(actor, departmentId);
        String intent = intent(question, true);
        if ("UNSUPPORTED".equals(intent)) return refused("OUT_OF_SCOPE");
        var facts = new ArrayList<Evidence>();
        var distribution = data.distribution();
        facts.add(
            new Evidence(
                "team-counts",
                "FACT",
                "在职人数" +
                distribution.employeeCount() +
                "，已完成人数" +
                distribution.completedCount() +
                "，未完成人数" +
                distribution.notCompletedCount() +
                "。每人采用最新有效报告。",
                "部门聚合 / " + data.ruleVersion()
            )
        );
        for (var d : distribution.typeDistribution())
            if (d.employeeCount() > 0) facts.add(
                new Evidence(
                    "team-" + d.typeCode(),
                    "FACT",
                    d.typeCode() +
                    "类型编码人数为" +
                    d.employeeCount() +
                    "。仅为编码分布，不代表偏好强度。",
                    "部门最新类型分布"
                )
            );
        if (!"TEAM_COUNTS".equals(intent)) for (
            int i = 0;
            i < data.collaborationTips().size();
            i++
        ) facts.add(
            new Evidence(
                "team-tip-" + i,
                "ACTION",
                data.collaborationTips().get(i),
                "通用协作演示规则 / " + data.ruleVersion()
            )
        );
        return answer(intent, facts, data.ruleVersion(), null);
    }

    private List<Selection> mockPlan(String intent, List<Evidence> evidence) {
        var sorted = new ArrayList<>(evidence);
        if (List.of("GROWTH", "COLLABORATION", "EXPAND").contains(intent)) sorted.sort(
            Comparator.comparing(e -> !"ACTION".equals(e.kind()))
        );
        return sorted
            .stream()
            .limit(6)
            .map(e -> new Selection(e.id(), "ACTION".equals(e.kind()) ? "TRY" : "SUMMARY"))
            .toList();
    }

    private AiAnswerVO answer(
        String intent,
        List<Evidence> evidence,
        String contextVersion,
        Long resultId
    ) {
        var prompts = references.prompts();
        String mode = "MOCK",
            reason = "LOCAL_DEMO";
        List<Selection> selection = mockPlan(intent, evidence);
        if ("OFF".equals(properties.getMode())) {
            mode = "RULE_FALLBACK";
            reason = "AI_DISABLED";
        } else if ("REMOTE".equals(properties.getMode())) {
            mode = "RULE_FALLBACK";
            reason = !properties.isExternalEnabled()
                ? "EXTERNAL_DISABLED"
                : !remote.configured() ? "REMOTE_NOT_CONFIGURED" : "REMOTE_UNAVAILABLE";
            if (properties.isExternalEnabled() && remote.configured()) try {
                selection = remote.plan(
                    prompts.get("instruction"),
                    new GroundedAiContext(intent, evidence)
                );
                validatePlan(selection, evidence);
                mode = "REMOTE";
                reason = "GROUNDED_SELECTION";
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                selection = mockPlan(intent, evidence);
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "AI planner unavailable exception:{}; using grounded rules",
                    exception.getClass().getSimpleName()
                );
            }
        }
        var paragraphs = new ArrayList<AiAnswerVO.Paragraph>();
        for (var item : selection) {
            var fact = evidence
                .stream()
                .filter(e -> e.id().equals(item.evidenceId()))
                .findFirst()
                .orElseThrow();
            String prefix = switch (item.frame()) {
                case "OBSERVE" -> "可结合具体任务核对这条记录与自己的体验：";
                case "TRY" -> "若适用于当前情境，可自愿尝试并观察：";
                case "REVIEW" -> "尝试后，可记录体验并复盘以下行动：";
                default -> "依据当前保存的信息：";
            };
            paragraphs.add(new AiAnswerVO.Paragraph(fact.id(), prefix + fact.text(), fact.basis()));
        }
        return new AiAnswerVO(
            mode,
            reason,
            prompts.get("version"),
            contextVersion,
            resultId,
            paragraphs,
            NOTICE
        );
    }

    private void validatePlan(List<Selection> selection, List<Evidence> evidence) {
        if (
            selection == null || selection.isEmpty() || selection.size() > 6
        ) throw new IllegalArgumentException("Invalid plan");
        var seen = new HashSet<String>();
        for (var item : selection) {
            var fact = evidence
                .stream()
                .filter(e -> e.id().equals(item.evidenceId()))
                .findFirst()
                .orElseThrow();
            if (
                !seen.add(item.evidenceId()) ||
                !("ACTION".equals(fact.kind())
                        ? List.of("TRY", "REVIEW")
                        : List.of("SUMMARY", "OBSERVE")).contains(item.frame())
            ) throw new IllegalArgumentException("Unsupported frame");
        }
    }
}
