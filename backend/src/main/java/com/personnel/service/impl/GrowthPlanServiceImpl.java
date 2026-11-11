package com.personnel.service.impl;

import com.personnel.service.GrowthPlanService;
import com.personnel.service.GrowthTemplateService;
import com.personnel.vo.GraphExplanationVO;
import com.personnel.vo.GrowthPlanVO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GrowthPlanServiceImpl implements GrowthPlanService {

    private final GrowthTemplateService templates;

    public GrowthPlanServiceImpl(GrowthTemplateService templates) {
        this.templates = templates;
    }

    private static final String BASIS =
        "周期、观察目标与复盘提示为本项目编写的通用行动模板，不是量表作者结论，也不代表干预效果已经验证。";

    @Override
    public List<GrowthPlanVO> generatePlans(GraphExplanationVO graph) {
        var template = templates.getEffective();
        if (graph == null || graph.matches() == null || graph.matches().isEmpty()) {
            return List.of(
                new GrowthPlanVO(
                    "plan-reflection",
                    template.fallbackContext(),
                    template.fallbackAction(),
                    template.suggestedCycle(),
                    template.observationTarget(),
                    template.reviewPrompt(),
                    "未命中图谱规则；以下仅为通用自我观察建议。" + BASIS,
                    null,
                    null,
                    List.of(),
                    "PROJECT_TEMPLATE",
                    template.version(),
                    template.contentHash()
                )
            );
        }
        return graph
            .matches()
            .stream()
            .map(match ->
                new GrowthPlanVO(
                    "plan-" + match.ruleId(),
                    match.context(),
                    match.action(),
                    template.suggestedCycle(),
                    template.observationTarget(),
                    template.reviewPrompt(),
                    "情境与行动沿用当次保存的图谱规则；关系依据见对应解释路径。" + BASIS,
                    match.ruleId(),
                    graph.graphVersion(),
                    match
                        .sources()
                        .stream()
                        .map(s -> s.id())
                        .distinct()
                        .toList(),
                    match.sources().isEmpty() ||
                        match
                            .sources()
                            .stream()
                            .anyMatch(s -> "PROJECT_DEMO".equals(s.evidenceLevel()))
                        ? "PROJECT_DEMO"
                        : "CURATED_REFERENCE",
                    template.version(),
                    template.contentHash()
                )
            )
            .toList();
    }
}
