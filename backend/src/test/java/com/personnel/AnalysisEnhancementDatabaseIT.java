package com.personnel;

import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Transactional
class AnalysisEnhancementDatabaseIT {
    @Autowired JobPreferenceService jobs;
    @Autowired TeamAnalysisService teams;
    @Autowired UserService users;
    @Autowired OrganizationService organizations;
    @Autowired KnowledgeGraphService graphs;
    @Autowired AssessmentService assessments;
    @Autowired AssessmentScaleService scales;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    String unique() { return UUID.randomUUID().toString().replace("-", "").substring(0,16); }
    DepartmentVO department() { return organizations.addDepartment(new DepartmentSaveReqDTO("分析测试部门", "t"+unique(), null, 0)); }
    PositionVO position(DepartmentVO department) { return organizations.addPosition(new PositionSaveReqDTO("探索测试岗位", "t"+unique(), department.id(), "演示")); }
    UserVO user(String role, Long department, Long position) { return users.createUser(new AdminUserCreateReqDTO("ja"+unique(),"Fixture123","分析测试",null,role,department,position)); }
    UserVO user(String role) { return user(role,null,null); }
    JobProfileCreateReqDTO request(PositionVO position,List<JobProfileCreateReqDTO.RuleWeight> rules) { return new JobProfileCreateReqDTO(position.id(),"job-"+unique(),"多维任务探索",graphs.getCurrent().id(),"项目演示整理，未验证岗位效度",rules); }
    JobProfileVO profile(UserVO admin,PositionVO position) { return jobs.createProfile(admin.id(),request(position,List.of(new JobProfileCreateReqDTO.RuleWeight(graphs.getCurrent().rules().get(0).id(),100)))); }
    AssessmentResultVO result(UserVO user,String type) {
        var scale=scales.listPublished().stream().filter(s->"OEJTS_OPENJUNG_32_V1".equals(s.definition().scoringVersion())).findFirst().orElseThrow();
        var answers=new LinkedHashMap<String,Integer>();
        for(var question:scale.definition().questions()) { int index=List.of("EI","SN","TF","JP").indexOf(question.dimension()); answers.put(question.key(),type.substring(index,index+1).equals(question.highPole())?5:1); }
        var session=assessments.startAssessment(user.id(),new AssessmentStartReqDTO(scale.id(),true));
        return assessments.submitAssessment(user.id(),session.id(),new AssessmentAnswersReqDTO(0,answers));
    }
    String matchingType(KnowledgeGraphVO.Rule rule) {
        var chars="INTJ".toCharArray(); for(var c:rule.conditions()) chars[List.of("EI","SN","TF","JP").indexOf(c.dimension())]=c.pole().charAt(0);return new String(chars);
    }
    String bearer(UserVO user) { return "Bearer "+jwt.createToken(user.id(),user.roleCode(),user.authVersion()); }

    @Test void validatesWeightsDuplicateRulesAndPermissions() {
        var admin=user("ADMIN");var position=position(department()); var rule=graphs.getCurrent().rules().get(0).id();
        assertThatThrownBy(()->jobs.createProfile(admin.id(),request(position,List.of(new JobProfileCreateReqDTO.RuleWeight(rule,99))))).hasMessageContaining("100");
        assertThatThrownBy(()->jobs.createProfile(admin.id(),request(position,List.of(new JobProfileCreateReqDTO.RuleWeight(rule,50),new JobProfileCreateReqDTO.RuleWeight(rule,50))))).hasMessageContaining("重复");
        assertThatThrownBy(()->jobs.createProfile(admin.id(),request(position,List.of(new JobProfileCreateReqDTO.RuleWeight("missing",100))))).hasMessageContaining("不存在");
        for(var role:List.of("EMPLOYEE","DEPARTMENT_MANAGER")) {
            var actor=user(role);assertThatThrownBy(()->jobs.listProfiles(actor.id())).hasMessageContaining("管理员");
            assertThatThrownBy(()->jobs.createProfile(actor.id(),request(position,List.of(new JobProfileCreateReqDTO.RuleWeight(rule,100))))).hasMessageContaining("管理员");
            assertThatThrownBy(()->teams.analyzeDepartment(actor.id(),0L)).hasMessageContaining("管理员");
        }
        var req=request(position,List.of(new JobProfileCreateReqDTO.RuleWeight(rule,100)));jobs.createProfile(admin.id(),req);
        assertThatThrownBy(()->jobs.createProfile(admin.id(),req)).hasMessageContaining("版本号已存在");
    }
    @Test void freezesSourcesAndComputesWeightedCoverageFromLatestSavedReport() {
        var admin=user("ADMIN"); var position=position(department()); var employee=user("EMPLOYEE",position.departmentId(),position.id());
        var rules=graphs.getCurrent().rules(); var req=request(position,List.of(new JobProfileCreateReqDTO.RuleWeight(rules.get(0).id(),35),new JobProfileCreateReqDTO.RuleWeight(rules.get(1).id(),65)));
        var profile=jobs.createProfile(admin.id(),req); assertThat(profile.definition().rules()).allSatisfy(r->{assertThat(r.sources()).isNotEmpty();assertThat(r.edges()).isNotEmpty();assertThat(r.nodes()).isNotEmpty();});
        assertThat(profile.contentHash()).hasSize(64);jobs.publishProfile(admin.id(),profile.id());
        result(employee,"ESFP");var saved=result(employee,matchingType(rules.get(0)));
        var data=jobs.explore(employee.id(),employee.id());assertThat(data.resultId()).isEqualTo(saved.id());
        var candidate=data.positions().stream().filter(c->c.profile().id().equals(profile.id())).findFirst().orElseThrow();
        var hits=saved.report().graphExplanation().matches().stream().map(GraphExplanationVO.Match::ruleId).toList();
        int expected=profile.definition().rules().stream().filter(r->hits.contains(r.rule().id())).mapToInt(JobProfileVO.WeightedRule::weight).sum();
        assertThat(candidate.comparable()).isTrue();assertThat(candidate.ruleCoveragePercent()).isEqualTo(expected).isGreaterThanOrEqualTo(35);
        assertThat(jobs.explore(admin.id(),employee.id()).resultId()).isEqualTo(saved.id());
        assertThatThrownBy(()->jobs.explore(user("DEPARTMENT_MANAGER").id(),employee.id())).hasMessageContaining("无权");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit WHERE target_type='JOB_PROFILE' AND target_id=?",Integer.class,profile.id())).isEqualTo(2);
    }
    @Test void draftsHiddenPublishArchiveFallbackAndDisabledPositionHidden() {
        var admin=user("ADMIN");var position=position(department());var employee=user("EMPLOYEE");result(employee,"INTJ");
        var first=profile(admin,position);assertThat(jobs.explore(employee.id(),employee.id()).positions()).noneMatch(c->c.profile().id().equals(first.id()));
        jobs.publishProfile(admin.id(),first.id());var second=profile(admin,position);jobs.publishProfile(admin.id(),second.id());
        assertThat(jobs.explore(employee.id(),employee.id()).positions()).anyMatch(c->c.profile().id().equals(second.id())).noneMatch(c->c.profile().id().equals(first.id()));
        jobs.archiveProfile(admin.id(),second.id());assertThat(jobs.explore(employee.id(),employee.id()).positions()).anyMatch(c->c.profile().id().equals(first.id()));
        assertThatThrownBy(()->jobs.publishProfile(admin.id(),first.id())).hasMessageContaining("草稿");
        organizations.setPositionEnabled(position.id(),false);assertThat(jobs.explore(employee.id(),employee.id()).positions()).noneMatch(c->c.profile().definition().positionId().equals(position.id()));
    }
    @Test void graphChangeDoesNotBackCalculateOldReportOrPublishStaleDraft() {
        var admin=user("ADMIN");var employee=user("EMPLOYEE");var position=position(department());var old=profile(admin,position);jobs.publishProfile(admin.id(),old.id());var draft=profile(admin,position);
        var saved=result(employee,"INTJ");
        var graph=graphs.getCurrent();
        var tree=json.valueToTree(graph);((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("version","graph-"+unique());
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree).remove(List.of("id","status","contentHash"));
        var replacement=graphs.importVersion(admin.id(),json.convertValue(tree,GraphImportReqDTO.class));graphs.publishVersion(admin.id(),replacement.id());
        assertThatThrownBy(()->jobs.publishProfile(admin.id(),draft.id())).hasMessageContaining("依据");
        var current=profile(admin,position);jobs.publishProfile(admin.id(),current.id());
        var candidate=jobs.explore(employee.id(),employee.id()).positions().stream().filter(c->c.profile().id().equals(current.id())).findFirst().orElseThrow();
        assertThat(candidate.comparable()).isFalse();assertThat(candidate.ruleCoveragePercent()).isNull();assertThat(candidate.matchedRuleIds()).isEmpty();
        assertThat(assessments.getResult(employee.id(),saved.id()).report().graphExplanation().contentHash()).isEqualTo(old.definition().graphContentHash());
    }
    @Test void emptyAssessmentAndEmptyDepartmentHaveExplicitStates() {
        var admin=user("ADMIN");var employee=user("EMPLOYEE");var department=department();
        assertThat(jobs.explore(employee.id(),employee.id()).resultId()).isNull();assertThat(jobs.explore(employee.id(),employee.id()).positions()).isEmpty();
        var data=teams.analyzeDepartment(admin.id(),department.id());assertThat(data.distribution().employeeCount()).isZero();assertThat(data.dimensions()).allSatisfy(d->{assertThat(d.firstCount()).isZero();assertThat(d.secondCount()).isZero();});
        assertThat(data.collaborationTips()).hasSize(1);assertThatThrownBy(()->teams.analyzeDepartment(admin.id(),-1L)).hasMessageContaining("有效部门");
    }
    @Test void departmentAggregationUsesLatestPerPersonAndNeverMixesOtherDepartments() {
        var admin=user("ADMIN");var department=department();var position=position(department);
        var a=user("EMPLOYEE",department.id(),position.id());var b=user("EMPLOYEE",department.id(),position.id());var unfinished=user("EMPLOYEE",department.id(),position.id());
        result(a,"INTJ");result(a,"ESFP");result(b,"INTJ");result(user("EMPLOYEE",department().id(),null),"ESTJ");
        var data=teams.analyzeDepartment(admin.id(),department.id());
        assertThat(data.distribution().employeeCount()).isEqualTo(3);assertThat(data.distribution().completedCount()).isEqualTo(2);assertThat(data.distribution().assessmentCount()).isEqualTo(3);
        assertThat(data.distribution().typeDistribution()).filteredOn(t->t.employeeCount()>0).extracting(TalentDashboardVO.TypeDistribution::typeCode).containsExactlyInAnyOrder("INTJ","ESFP");
        assertThat(data.dimensions()).allSatisfy(d->{assertThat(d.firstCount()).isEqualTo(1);assertThat(d.secondCount()).isEqualTo(1);});assertThat(data.collaborationTips()).hasSize(5);
    }
    @Test void httpRoutesRequireLoginAndAdminWhileSelfRouteWorks() throws Exception {
        var admin=user("ADMIN");var employee=user("EMPLOYEE");var manager=user("DEPARTMENT_MANAGER");
        mvc.perform(get("/api/job-preferences")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/job-preferences").header("Authorization",bearer(employee))).andExpect(jsonPath("$.code").value(200));
        for(var actor:List.of(employee,manager)) for(var route:List.of("/api/admin/job-preferences","/api/admin/job-preferences/users/"+employee.id(),"/api/admin/team-analysis?departmentId=0")) mvc.perform(get(route).header("Authorization",bearer(actor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/team-analysis?departmentId=0").header("Authorization",bearer(admin))).andExpect(jsonPath("$.code").value(200));
    }
}
