package com.personnel;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.AuditActorContext;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.OperationAuditMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.sql.init.mode=never") @AutoConfigureMockMvc @Transactional
@Sql(scripts="classpath:growth-template-schema.sql",executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS,
        config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED))
class GrowthTemplateDatabaseIT {
    @Autowired GrowthTemplateService templates;
    @Autowired GrowthPlanService plans;
    @Autowired AssessmentService assessments;
    @Autowired AssessmentScaleService scales;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @SpyBean OperationAuditMapper audits;
    String unique() { return "gt"+UUID.randomUUID().toString().replace("-","").substring(0,16); }
    UserVO account(String role) { return users.createUser(new AdminUserCreateReqDTO(unique(),"TestOnly123","模板测试",null,role,null,null)); }
    String bearer(UserVO user) { return "Bearer "+jwt.createToken(user.id(),user.roleCode()); }
    GrowthTemplateCreateReqDTO request(String version) { return new GrowthTemplateCreateReqDTO(version," 自建模板 ","按实际安排自愿尝试","只记录体验","复盘适用情境","通用任务","记录真实体验"); }
    @AfterEach void cleanup() { reset(audits); AuditActorContext.clear(); }

    @Test void immutableLifecycleLatestImportedWinsAndArchiveFallsBack() {
        var admin=account("ADMIN"); AuditActorContext.set(admin.id(),"ADMIN");
        var first=templates.createVersion(admin.id(),request(unique()));
        var second=templates.createVersion(admin.id(),request(unique()));
        assertThat(first.status()).isEqualTo("DRAFT"); assertThat(first.title()).isEqualTo("自建模板");
        assertThat(first.contentHash()).matches("[0-9a-f]{64}");
        assertThat(templates.getEffective().id()).isNotEqualTo(first.id());
        templates.publishVersion(admin.id(),second.id()); templates.publishVersion(admin.id(),first.id());
        assertThat(templates.getEffective().id()).isEqualTo(second.id());
        assertThatThrownBy(() -> templates.publishVersion(admin.id(),first.id())).hasMessageContaining("草稿");
        assertThatThrownBy(() -> templates.createVersion(admin.id(),request(first.version()))).hasMessageContaining("版本号已存在");
        templates.archiveVersion(admin.id(),second.id()); assertThat(templates.getEffective().id()).isEqualTo(first.id());
        templates.archiveVersion(admin.id(),first.id());
        assertThat(templates.listVersions(admin.id())).filteredOn(row -> row.id().equals(first.id())).singleElement()
                .satisfies(row -> { assertThat(row.suggestedCycle()).isEqualTo(first.suggestedCycle()); assertThat(row.contentHash()).isEqualTo(first.contentHash()); });
        assertThat(audits.selectCount(Wrappers.<OperationAuditDO>lambdaQuery().eq(OperationAuditDO::getTargetId,first.id())
                .eq(OperationAuditDO::getOperation,"TEMPLATE_PUBLISH"))).isEqualTo(1);
    }
    @Test void noPublishedTemplateUsesBuiltInAndCustomFallbackRetainsFixedDisclaimer() {
        jdbc.update("UPDATE growth_template SET status='ARCHIVED' WHERE status='PUBLISHED'");
        assertThat(templates.getEffective().version()).isEqualTo("BUILTIN_V1");
        var admin=account("ADMIN"); var template=templates.createVersion(admin.id(),request(unique()));
        templates.publishVersion(admin.id(),template.id());
        var plan=plans.generatePlans(null).get(0);
        assertThat(plan.templateVersion()).isEqualTo(template.version()); assertThat(plan.templateContentHash()).isEqualTo(template.contentHash());
        assertThat(plan.action()).isEqualTo(template.fallbackAction()); assertThat(plan.context()).isEqualTo(template.fallbackContext());
        assertThat(plan.basis()).contains("不是量表作者结论","不代表干预效果已经验证").doesNotContain("已验证有效");
        templates.archiveVersion(admin.id(),template.id()); assertThat(templates.getEffective().version()).isEqualTo("BUILTIN_V1");
    }
    @Test void reportFreezesTemplateAtSubmissionAndOldReportDoesNotChange() {
        var admin=account("ADMIN"); var employee=account("EMPLOYEE");
        var scale=scales.importScale(admin.id(),AssessmentFixtures.scale(unique(),"ORIGINAL-TEST-FIXTURE"));
        scales.publishScale(admin.id(),scale.id(),new ScalePublishReqDTO(true,true,true));
        var first=templates.createVersion(admin.id(),request(unique())); templates.publishVersion(admin.id(),first.id());
        var draft=assessments.startAssessment(employee.id(),new AssessmentStartReqDTO(scale.id(),true));
        var result=assessments.submitAssessment(employee.id(),draft.id(),new AssessmentAnswersReqDTO(0,AssessmentFixtures.answers("INTP")));
        assertThat(result.report().growthPlans()).allSatisfy(plan -> assertThat(plan.templateVersion()).isEqualTo(first.version()));
        var nextDraft=assessments.startAssessment(employee.id(),new AssessmentStartReqDTO(scale.id(),true));
        var second=templates.createVersion(admin.id(),new GrowthTemplateCreateReqDTO(unique(),"第二版","NEW_CYCLE","NEW_OBSERVATION","NEW_REVIEW","任务","行动"));
        templates.publishVersion(admin.id(),second.id());
        var next=assessments.submitAssessment(employee.id(),nextDraft.id(),new AssessmentAnswersReqDTO(0,AssessmentFixtures.answers("INTP")));
        assertThat(next.report().growthPlans()).allSatisfy(plan -> {
            assertThat(plan.templateVersion()).isEqualTo(second.version()); assertThat(plan.suggestedCycle()).isEqualTo("NEW_CYCLE");
        });
        templates.archiveVersion(admin.id(),second.id());
        assertThat(assessments.getResult(employee.id(),draft.id())).isEqualTo(result);
        assertThat(assessments.getResult(employee.id(),nextDraft.id())).isEqualTo(next);
    }
    @Test void apiEnforcesRolesValidationAndServerActorAndAuditFilter() throws Exception {
        mvc.perform(get("/api/admin/growth-templates")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"EMPLOYEE","DEPARTMENT_MANAGER"}) {
            var user=account(role);
            mvc.perform(get("/api/admin/growth-templates/effective").header("Authorization",bearer(user))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/growth-templates").header("Authorization",bearer(user)).contentType("application/json")
                    .content(json.writeValueAsBytes(request(unique())))).andExpect(status().isForbidden());
            assertThatThrownBy(() -> templates.createVersion(user.id(),request(unique()))).hasMessageContaining("管理员");
        }
        var admin=account("ADMIN");
        for(var invalid:new GrowthTemplateCreateReqDTO[]{request("bad space"),request("BUILTIN_V1"),
                new GrowthTemplateCreateReqDTO(unique()," ","x","x","x","x","x"),
                new GrowthTemplateCreateReqDTO(unique(),"x","x".repeat(501),"x","x","x","x")}) {
            mvc.perform(post("/api/admin/growth-templates").header("Authorization",bearer(admin)).contentType("application/json")
                    .content(json.writeValueAsBytes(invalid))).andExpect(jsonPath("$.code").value(400));
        }
        var response=mvc.perform(post("/api/admin/growth-templates").header("Authorization",bearer(admin)).param("actorUserId","999999")
                .contentType("application/json").content(json.writeValueAsBytes(request(unique())))).andExpect(jsonPath("$.code").value(200)).andReturn();
        long id=json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
        var entry=audits.selectOne(Wrappers.<OperationAuditDO>lambdaQuery().eq(OperationAuditDO::getTargetId,id).eq(OperationAuditDO::getOperation,"TEMPLATE_CREATE"));
        assertThat(entry.getActorUserId()).isEqualTo(admin.id()); assertThat(entry.getChangedFields()).doesNotContain("按实际安排","自建模板");
        mvc.perform(get("/api/admin/operation-audits").header("Authorization",bearer(admin)).param("targetType","GROWTH_TEMPLATE")
                .param("operation","TEMPLATE_CREATE").param("targetId",Long.toString(id))).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/api/admin/growth-templates/"+id).header("Authorization",bearer(admin))).andExpect(status().is4xxClientError());
        assertThat(AuditActorContext.get()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"TEMPLATE_CREATE","TEMPLATE_PUBLISH","TEMPLATE_ARCHIVE"})
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failedAuditRollsBackEachMutation(String operation) {
        String marker=unique();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            var admin=account("ADMIN"); AuditActorContext.set(admin.id(),"ADMIN");
            doThrow(new DataAccessResourceFailureException("simulated audit failure")).when(audits)
                    .insert(argThat((OperationAuditDO row) -> row!=null && operation.equals(row.getOperation())));
            var value=templates.createVersion(admin.id(),request(marker));
            if(!operation.equals("TEMPLATE_CREATE")) templates.publishVersion(admin.id(),value.id());
            if(operation.equals("TEMPLATE_ARCHIVE")) templates.archiveVersion(admin.id(),value.id());
            return null;
        })).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM growth_template WHERE version=?",Long.class,marker)).isZero();
    }
}
