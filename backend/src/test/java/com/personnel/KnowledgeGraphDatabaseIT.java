package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class KnowledgeGraphDatabaseIT {
    @Autowired KnowledgeGraphService graphs;
    @Autowired AssessmentScaleService scales;
    @Autowired AssessmentService assessments;
    @Autowired AssessmentScoringService scoring;
    @Autowired UserService users;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    String unique() { return "kg"+UUID.randomUUID().toString().replace("-", "").substring(0,16); }
    UserVO account(String role) { return users.createUser(new AdminUserCreateReqDTO(unique(),"TestOnly123","图谱验收",null,role,null,null)); }
    String bearer(UserVO user) { return "Bearer "+jwt.createToken(user.id(),user.roleCode()); }
    AssessmentScaleVO reference() { return scales.listPublished().stream().filter(s->s.definition().scoringVersion().equals("OEJTS_OPENJUNG_32_V1")).findFirst().orElseThrow(); }
    Map<String,Integer> answers(String type) {
        var result=new LinkedHashMap<String,Integer>();
        for(var q:reference().definition().questions()) {
            int index=List.of("EI","SN","TF","JP").indexOf(q.dimension());
            result.put(q.key(),q.highPole().equals(type.substring(index,index+1))?5:1);
        } return result;
    }
    GraphImportReqDTO copy(String version,List<GraphImportReqDTO.Rule> replacement) throws Exception {
        var original=json.readValue(json.writeValueAsBytes(graphs.getCurrent()),GraphImportReqDTO.class);
        return new GraphImportReqDTO(version,original.title(),original.sources(),original.nodes(),original.edges(),replacement==null?original.rules():replacement);
    }
    @Test void allSixteenTypesAndTfMidpointMatchFixedUpstreamContract() {
        var scale=reference().definition(); assertThat(scale.questions()).hasSize(32);
        var dimensionByRemainder=Map.of(1,"JP",2,"TF",3,"EI",0,"SN");
        for(int id=1;id<=32;id++) {
            int expected=id; var question=scale.questions().stream().filter(q->q.key().equals("oejts"+expected)).findFirst().orElseThrow();
            assertThat(question.dimension()).isEqualTo(dimensionByRemainder.get(id%4));
            assertThat(question.highPole()).isEqualTo(Map.of("EI","I","SN","N","TF","T","JP","P").get(question.dimension()));
            assertThat(question.weight()).isEqualTo(1); assertThat(question.leftText()).isNotBlank(); assertThat(question.rightText()).isNotBlank();
        }
        for(char e:"EI".toCharArray()) for(char s:"SN".toCharArray()) for(char t:"TF".toCharArray()) for(char j:"JP".toCharArray()) {
            String type=""+e+s+t+j;
            var dimensions=scoring.score(scale,answers(type));
            assertThat(dimensions.stream().map(AssessmentDimensionVO::preferredPole).reduce("",String::concat)).isEqualTo(type);
            assertThat(dimensions).allSatisfy(d->assertThat(d.firstPercent().add(d.secondPercent())).isEqualByComparingTo("100"));
        }
        var neutral=new LinkedHashMap<String,Integer>(); scale.questions().forEach(q->neutral.put(q.key(),3));
        assertThat(scoring.score(scale,neutral).stream().map(AssessmentDimensionVO::preferredPole).reduce("",String::concat)).isEqualTo("ESFJ");
        assertThat(graphs.explain(scoring.score(scale,neutral)).matches()).isEmpty();
        var uniform=new LinkedHashMap<String,Integer>(); scale.questions().forEach(q->uniform.put(q.key(),4));
        var upper=scoring.score(scale,uniform);
        assertThat(upper.stream().map(AssessmentDimensionVO::preferredPole).reduce("",String::concat)).isEqualTo("INTP");
        assertThat(upper.get(0).firstPercent()).isEqualByComparingTo("25");
        assertThat(upper.get(2).firstPercent()).isEqualByComparingTo("75");
    }
    @Test void multiPremisePathsNeedAllConditionsAndAvoidWeakOrBalancedDimensions() {
        var scale=reference().definition(); var full=answers("INTJ");
        var explanation=graphs.explain(scoring.score(scale,full));
        assertThat(explanation.matches()).extracting(GraphExplanationVO.Match::ruleId).containsExactly("rule-in","rule-tj");
        assertThat(explanation.matches()).allSatisfy(m->{ assertThat(m.matchedConditions()).hasSize(2); assertThat(m.edges()).hasSize(6);
            assertThat(m.edges()).allMatch(e->e.sourceId().equals("project-demo"));
            assertThat(m.sources()).extracting(KnowledgeGraphVO.Source::evidenceLevel).containsExactlyInAnyOrder("PROJECT_DEMO","CURATED_REFERENCE"); });
        scale.questions().stream().filter(q->q.dimension().equals("SN")).forEach(q->full.put(q.key(),3));
        assertThat(graphs.explain(scoring.score(scale,full)).matches()).extracting(GraphExplanationVO.Match::ruleId).doesNotContain("rule-in");
        // N=59.375% stays below the explicit 60% demonstration threshold.
        var nQuestions=scale.questions().stream().filter(q->q.dimension().equals("SN")).toList();
        for(int i=0;i<3;i++) full.put(nQuestions.get(i).key(),4);
        assertThat(graphs.explain(scoring.score(scale,full)).matches()).extracting(GraphExplanationVO.Match::ruleId).doesNotContain("rule-in");
        full.put(nQuestions.get(3).key(),4);
        assertThat(graphs.explain(scoring.score(scale,full)).matches()).extracting(GraphExplanationVO.Match::ruleId).contains("rule-in");
        var rounded=new ArrayList<>(scoring.score(scale,answers("INTJ")));
        rounded.set(0,new AssessmentDimensionVO("EI",3323,1278,6390,new java.math.BigDecimal("40.00"),new java.math.BigDecimal("60.00"),"I",false,false));
        assertThat(graphs.explain(rounded).matches()).extracting(GraphExplanationVO.Match::ruleId).doesNotContain("rule-in");
    }
    @Test void completedReportFreezesGraphEvenWhenLatestVersionChanges() throws Exception {
        var admin=account("ADMIN"); var employee=account("EMPLOYEE"); var scale=reference();
        var draft=assessments.startAssessment(employee.id(),new AssessmentStartReqDTO(scale.id(),true));
        var result=assessments.submitAssessment(employee.id(),draft.id(),new AssessmentAnswersReqDTO(0,answers("INTJ")));
        assertThat(result.report().graphExplanation().matches()).hasSize(2);
        assertThat(result.report().reportVersion()).isEqualTo("GRAPH_GROWTH_PLAN_V3");
        assertThat(result.report().growthPlans()).hasSize(2).allSatisfy(plan -> {
            var match = result.report().graphExplanation().matches().stream().filter(m -> m.ruleId().equals(plan.ruleId())).findFirst().orElseThrow();
            assertThat(plan.context()).isEqualTo(match.context());
            assertThat(plan.action()).isEqualTo(match.action());
            assertThat(plan.graphVersion()).isEqualTo(result.report().graphExplanation().graphVersion());
            assertThat(plan.sourceIds()).containsExactlyElementsOf(match.sources().stream().map(KnowledgeGraphVO.Source::id).toList());
        });
        var next=graphs.importVersion(admin.id(),copy(unique(),List.of())); graphs.publishVersion(admin.id(),next.id());
        assertThat(graphs.explain(scoring.score(scale.definition(),answers("INTJ"))).matches()).isEmpty();
        assertThat(assessments.getResult(employee.id(),draft.id())).isEqualTo(result);
        mvc.perform(get("/api/assessment/results/"+draft.id()).header("Authorization",bearer(account("DEPARTMENT_MANAGER")))).andExpect(jsonPath("$.code").value(400));
    }
    @Test void graphCrudRejectsBrokenPathsAndNonAdminAndKeepsVersionImmutable() throws Exception {
        var admin=account("ADMIN"); var employee=account("EMPLOYEE"); var current=graphs.getCurrent();
        assertThat(current.nodes()).hasSize(100); assertThat(current.edges()).hasSize(152);
        mvc.perform(get("/api/knowledge-graph")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/knowledge-graph").header("Authorization",bearer(employee))).andExpect(jsonPath("$.data.rules.length()").value(8));
        var valid=copy(unique(),null);
        mvc.perform(post("/api/admin/knowledge-graph").header("Authorization",bearer(employee)).contentType("application/json").content(json.writeValueAsBytes(valid))).andExpect(status().isForbidden());
        var broken=new ArrayList<>(valid.rules()); var rule=broken.get(0);
        broken.set(0,new GraphImportReqDTO.Rule(rule.id(),rule.actionId(),rule.contextId(),rule.explanation(),rule.conditions(),List.of(rule.edgeIds().get(0),rule.edgeIds().get(1))));
        assertThatThrownBy(()->graphs.importVersion(admin.id(),new GraphImportReqDTO(unique(),valid.title(),valid.sources(),valid.nodes(),valid.edges(),broken))).hasMessageContaining("路径");
        var added=graphs.importVersion(admin.id(),valid); assertThat(added.status()).isEqualTo("DRAFT");
        assertThat(graphs.getCurrent().id()).isEqualTo(current.id());
        graphs.publishVersion(admin.id(),added.id()); assertThat(graphs.getCurrent().id()).isEqualTo(added.id());
        assertThatThrownBy(()->graphs.publishVersion(admin.id(),added.id())).hasMessageContaining("草稿");
        assertThatThrownBy(()->graphs.importVersion(admin.id(),valid)).hasMessageContaining("已存在");
        graphs.archiveVersion(admin.id(),added.id()); assertThat(graphs.getCurrent().id()).isEqualTo(current.id());
    }
    @Test void noPublishedGraphFallsBackAndLegacyReportsRemainReadable() throws Exception {
        var admin=account("ADMIN");
        for(var graph:graphs.listVersions(admin.id())) if(graph.status().equals("PUBLISHED")) graphs.archiveVersion(admin.id(),graph.id());
        var explanation=graphs.explain(scoring.score(reference().definition(),answers("INTJ")));
        assertThat(explanation.matches()).isEmpty(); assertThat(explanation.graphVersion()).isNull();
        var employee=account("EMPLOYEE"); var draft=assessments.startAssessment(employee.id(),new AssessmentStartReqDTO(reference().id(),true));
        var result=assessments.submitAssessment(employee.id(),draft.id(),new AssessmentAnswersReqDTO(0,answers("INTJ")));
        assertThat(result.report().graphExplanation().matches()).isEmpty();
        assertThat(result.report().growthPlans()).hasSize(1);
        assertThat(result.report().growthPlans().get(0).ruleId()).isNull();
        var legacy=json.valueToTree(result.report()); ((com.fasterxml.jackson.databind.node.ObjectNode)legacy).remove("graphExplanation");
        assertThat(json.treeToValue(legacy,AssessmentReportVO.class).graphExplanation()).isNull();
        ((com.fasterxml.jackson.databind.node.ObjectNode)legacy).remove("growthPlans");
        assertThat(json.treeToValue(legacy,AssessmentReportVO.class).growthPlans()).isNull();
    }
}
