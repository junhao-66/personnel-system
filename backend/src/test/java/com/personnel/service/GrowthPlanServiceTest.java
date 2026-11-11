package com.personnel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.service.impl.GrowthPlanServiceImpl;
import com.personnel.vo.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GrowthPlanServiceTest {
    private final GrowthTemplateService templates = org.mockito.Mockito.mock(GrowthTemplateService.class);
    private final GrowthPlanService service = new GrowthPlanServiceImpl(templates);
    @org.junit.jupiter.api.BeforeEach void setup() { org.mockito.Mockito.when(templates.getEffective()).thenReturn(com.personnel.convert.GrowthTemplateConvert.builtIn()); }
    @Test void preservesRuleEvidenceAndDistinguishesTemplateProvenance() {
        var source = new KnowledgeGraphVO.Source("s1", "演示", "项目", "https://example.org", LocalDate.now(), "demo", "署名", "v1", "PROJECT_DEMO");
        var match = new GraphExplanationVO.Match("r1", "讨论", "先整理方案", "关联", List.of("I>=60"), List.of(), List.of(), List.of(source));
        var graph = new GraphExplanationVO("g1", "hash", "说明", List.of(match));
        var plan = service.generatePlans(graph).get(0);
        assertThat(plan.context()).isEqualTo(match.context());
        assertThat(plan.action()).isEqualTo(match.action());
        assertThat(plan.ruleId()).isEqualTo("r1");
        assertThat(plan.graphVersion()).isEqualTo("g1");
        assertThat(plan.sourceIds()).containsExactly("s1");
        assertThat(plan.evidenceLevel()).isEqualTo("PROJECT_DEMO");
        assertThat(plan.basis()).contains("本项目", "不是量表作者结论");
        assertThat(service.generatePlans(graph)).isEqualTo(service.generatePlans(graph));
        assertThat(graph.matches()).containsExactly(match);
    }
    @Test void noGraphDoesNotInventRuleOrSource() {
        var plan = service.generatePlans(null).get(0);
        assertThat(plan.ruleId()).isNull(); assertThat(plan.graphVersion()).isNull();
        assertThat(plan.sourceIds()).isEmpty(); assertThat(plan.evidenceLevel()).isEqualTo("PROJECT_TEMPLATE");
        assertThat(service.generatePlans(new GraphExplanationVO("g", "h", "", List.of()))).containsExactly(plan);
    }
    @Test void configuredTemplateChangesEditorialFieldsButPreservesMatchedGraphAction() {
        var template = new GrowthTemplateVO(7L,"custom-v1","自建模板","PUBLISHED","hash-v1","自愿尝试","记录反馈","复盘情境","备用情境","备用行动");
        org.mockito.Mockito.when(templates.getEffective()).thenReturn(template);
        var match = new GraphExplanationVO.Match("r1","图谱情境","图谱行动","关联",List.of(),List.of(),List.of(),List.of());
        var plan = service.generatePlans(new GraphExplanationVO("g1","graph-hash","",List.of(match))).get(0);
        assertThat(plan.action()).isEqualTo("图谱行动"); assertThat(plan.context()).isEqualTo("图谱情境");
        assertThat(plan.suggestedCycle()).isEqualTo("自愿尝试"); assertThat(plan.observationTarget()).isEqualTo("记录反馈");
        assertThat(plan.templateVersion()).isEqualTo("custom-v1"); assertThat(plan.templateContentHash()).isEqualTo("hash-v1");
        assertThat(plan.sourceIds()).isEmpty(); assertThat(plan.ruleId()).isEqualTo("r1");
    }
    @Test void legacyReportKeepsOriginalTextWithoutSynthesizingPlans() throws Exception {
        var report = new ObjectMapper().readValue("{\"reportVersion\":\"GRAPH_EXPLANATION_V1\",\"growthSuggestions\":[\"旧建议\"]}", AssessmentReportVO.class);
        assertThat(report.growthPlans()).isNull();
        assertThat(report.growthSuggestions()).containsExactly("旧建议");
    }
}
