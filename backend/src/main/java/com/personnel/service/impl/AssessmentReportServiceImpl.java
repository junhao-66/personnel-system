package com.personnel.service.impl;

import com.personnel.common.constant.AssessmentConstants;
import com.personnel.service.AssessmentReportService;
import com.personnel.service.GrowthPlanService;
import com.personnel.service.KnowledgeGraphService;
import com.personnel.vo.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Combines basic editorial sections with source-traceable conditional graph guidance, frozen on submission. */
@Service
public class AssessmentReportServiceImpl implements AssessmentReportService {

    private final KnowledgeGraphService graphs;
    private final GrowthPlanService growthPlans;

    public AssessmentReportServiceImpl(
        KnowledgeGraphService graphs,
        GrowthPlanService growthPlans
    ) {
        this.graphs = graphs;
        this.growthPlans = growthPlans;
    }

    private static final Map<String, String> PREFERENCES = Map.of(
        "E",
        "可能更偏好通过交流整理想法",
        "I",
        "可能更偏好先独立思考再交流",
        "S",
        "可能更偏好结合具体信息和经验",
        "N",
        "可能更偏好探索整体关联和可能性",
        "T",
        "可能更偏好先梳理规则及逻辑",
        "F",
        "可能更偏好先考虑人的感受及价值",
        "J",
        "可能更偏好提前安排任务",
        "P",
        "可能更偏好保留调整空间"
    );

    @Override
    public AssessmentReportVO generateReport(
        ScaleDefinitionVO scale,
        List<AssessmentDimensionVO> dimensions
    ) {
        var summaries = dimensions
            .stream()
            .map(
                d ->
                    d.dimension() +
                    "：" +
                    (d.weakPreference()
                            ? "偏好较弱或接近中点，请结合具体情境理解"
                            : PREFERENCES.get(d.preferredPole()))
            )
            .toList();
        boolean isDemo = "PROJECT-DEMO-ONLY".equals(scale.license());
        String disclaimer = isDemo
            ? "毕设演示报告：题目为本项目原创教学示例，未经信效度验证；结果仅展示系统流程，不用于正式人格评估。" +
            AssessmentConstants.DISCLAIMER
            : AssessmentConstants.DISCLAIMER;
        if (AssessmentConstants.OEJTS_SCORING_VERSION.equals(scale.scoringVersion())) disclaimer =
            "非商业毕设参考：OEJTS的OpenJung中文适配，题目及译本CC BY-NC-SA4.0，中文版本未独立验证。" +
            disclaimer;
        var graph = graphs.explain(dimensions);
        var growth = graph != null && !graph.matches().isEmpty()
            ? graph
                .matches()
                .stream()
                .map(m -> "在“" + m.context() + "”情境下，可尝试：" + m.action())
                .toList()
            : List.of(
                "本次未命中多维图谱规则，可先记录真实任务中的体验；不据此推断缺少优势或发展潜力。"
            );
        return new AssessmentReportVO(
            AssessmentConstants.REPORT_VERSION,
            disclaimer,
            summaries,
            List.of(
                "可把自己在不同情境下更舒适的处理方式作为反思线索；本报告不据此认定已具备任何技能。"
            ),
            List.of("可尝试与惯常偏好不同的方式，并通过真实任务反馈验证是否有帮助。"),
            summaries,
            List.of(
                "可在实际项目、访谈或短期体验中探索工作方式，结合兴趣、技能与任务要求判断，不按人格类型决定岗位录用。"
            ),
            growth,
            (isDemo
                    ? "演示题目依据：" +
                    scale.sourceTitle() +
                    "；" +
                    scale.author() +
                    "。无外部正式量表来源或测量效度声明。"
                    : "量表依据：" +
                    scale.sourceTitle() +
                    "；" +
                    scale.author() +
                    "；" +
                    scale.sourceUrl() +
                    "；访问日期：" +
                    scale.accessedDate()) +
            "。行动建议依据本次保存的图谱路径；项目演示关系不代表量表作者结论。其他基础段落仍为通用反思模板。",
            "自陈结果可能受当时情境和理解方式影响。百分比分数是归一化偏好刻度，不是能力、概率或优劣。接近中点时倾向较弱；恰好相同时按该评分版本约定编码并标记均衡，不表示真实偏好。历史差异不解释为能力提升或退步。图谱是有条件的关联解释，不是科学因果或能力判断。管理员可查看完整结果报告。保存期限：" +
            scale.retentionPolicy() +
            "；联系渠道：" +
            scale.contact(),
            graph,
            growthPlans.generatePlans(graph)
        );
    }
}
