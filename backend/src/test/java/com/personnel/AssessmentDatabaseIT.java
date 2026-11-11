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

/** Opt-in MySQL checks. Synthetic fixtures and user records roll back after every test. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class AssessmentDatabaseIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired UserService users;
    @Autowired AssessmentScaleService scales;
    @Autowired AssessmentService assessments;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    String unique() { return "as" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    UserVO account(String role) { return users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "测评测试", null, role, null, null)); }
    String bearer(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode()); }
    AssessmentScaleVO published(UserVO admin) {
        var value = scales.importScale(admin.id(), AssessmentFixtures.scale(unique(), "ORIGINAL-TEST-FIXTURE"));
        scales.publishScale(admin.id(), value.id(), new ScalePublishReqDTO(true, true, true)); return value;
    }
    @Test void draftSaveStrictValidationSubmitAndOwnership() throws Exception {
        var admin = account("ADMIN"); var employee = account("EMPLOYEE"); var manager = account("DEPARTMENT_MANAGER");
        var scale = published(admin);
        mvc.perform(post("/api/assessment/sessions").header("Authorization", bearer(employee)).contentType("application/json")
                .content("{\"scaleId\":" + scale.id() + ",\"consentAccepted\":false}")).andExpect(jsonPath("$.code").value(400));
        var draft = assessments.startAssessment(employee.id(), new AssessmentStartReqDTO(scale.id(), true));
        assertThat(assessments.startAssessment(employee.id(), new AssessmentStartReqDTO(scale.id(), true)).id()).isEqualTo(draft.id());
        for (String invalid : List.of("1.5", "\"3\"", "0", "null")) {
            mvc.perform(put("/api/assessment/sessions/" + draft.id() + "/answers").header("Authorization", bearer(employee)).contentType("application/json")
                    .content("{\"revision\":0,\"answers\":{\"EI1\":" + invalid + "}}" )).andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(put("/api/assessment/sessions/" + draft.id() + "/answers").header("Authorization", bearer(employee)).contentType("application/json")
                .content(json.writeValueAsBytes(new AssessmentAnswersReqDTO(0, Map.of("EI1", 5))))).andExpect(jsonPath("$.data.revision").value(1));
        assertThat(assessments.getCurrent(employee.id()).answers()).containsEntry("EI1", 5);
        mvc.perform(put("/api/assessment/sessions/" + draft.id() + "/answers").header("Authorization", bearer(employee)).contentType("application/json")
                .content(json.writeValueAsBytes(new AssessmentAnswersReqDTO(0, Map.of("EI1", 1))))).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/assessment/sessions/" + draft.id() + "/submit").header("Authorization", bearer(employee)).contentType("application/json")
                .content(json.writeValueAsBytes(new AssessmentAnswersReqDTO(1, Map.of("EI1", 5))))).andExpect(jsonPath("$.code").value(400));
        var request = new AssessmentAnswersReqDTO(1, AssessmentFixtures.answers("INTP"));
        var result = assessments.submitAssessment(employee.id(), draft.id(), request);
        assertThat(result.typeCode()).isEqualTo("INTP");
        assertThat(assessments.submitAssessment(employee.id(), draft.id(), request)).isEqualTo(result);
        assertThat(assessments.getCurrent(employee.id())).isNull();
        assertThat(assessments.getResult(admin.id(), draft.id())).isEqualTo(result);
        mvc.perform(get("/api/assessment/results/" + draft.id()).header("Authorization", bearer(manager))).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/assessment/sessions/" + draft.id()).header("Authorization", bearer(admin))).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/assessment/history").header("Authorization", bearer(employee))).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/assessment/history").header("Authorization", bearer(employee)).param("pageSize", "101")).andExpect(jsonPath("$.code").value(400));
        assertThatThrownBy(() -> assessments.saveAnswers(employee.id(), draft.id(), request)).isInstanceOf(RuntimeException.class);
    }
    @Test void archiveKeepsFrozenDraftAndHistoryAndComparisonChecksVersion() {
        var admin = account("ADMIN"); var employee = account("EMPLOYEE"); var scale = published(admin);
        var draft = assessments.startAssessment(employee.id(), new AssessmentStartReqDTO(scale.id(), true));
        scales.archiveScale(admin.id(), scale.id());
        assertThat(scales.listPublished()).noneMatch(s -> s.id().equals(scale.id()));
        var first = assessments.submitAssessment(employee.id(), draft.id(), new AssessmentAnswersReqDTO(0, AssessmentFixtures.answers("ESTJ")));
        assertThat(first.scale().version()).isEqualTo(scale.definition().version());
        assertThatThrownBy(() -> assessments.startAssessment(employee.id(), new AssessmentStartReqDTO(scale.id(), true))).isInstanceOf(RuntimeException.class);
        var secondScale = published(admin);
        var second = assessments.startAssessment(employee.id(), new AssessmentStartReqDTO(secondScale.id(), true));
        assessments.submitAssessment(employee.id(), second.id(), new AssessmentAnswersReqDTO(0, AssessmentFixtures.answers("INFP")));
        assertThat(assessments.compareResults(employee.id(), draft.id(), second.id()).comparable()).isFalse();
        var third = assessments.startAssessment(employee.id(), new AssessmentStartReqDTO(secondScale.id(), true));
        assessments.submitAssessment(employee.id(), third.id(), new AssessmentAnswersReqDTO(0, AssessmentFixtures.answers("ENTJ")));
        assertThat(assessments.compareResults(employee.id(), second.id(), third.id()).comparable()).isTrue();
        assertThat(assessments.listHistory(admin.id(), employee.id(), new AssessmentQueryReqDTO()).total()).isEqualTo(3);
    }
    @Test void importAndPublicationAreAdminOnlyAndRequireAttestations() throws Exception {
        var admin = account("ADMIN"); var employee = account("EMPLOYEE");
        var scale = scales.importScale(admin.id(), AssessmentFixtures.scale(unique(), "ORIGINAL-TEST-FIXTURE"));
        assertThat(scales.listPublished()).noneMatch(s -> s.id().equals(scale.id()));
        mvc.perform(post("/api/admin/assessment/scales").header("Authorization", bearer(employee)).contentType("application/json")
                .content(json.writeValueAsBytes(AssessmentFixtures.scale(unique(), "ORIGINAL-TEST-FIXTURE")))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/assessment/scales/" + scale.id() + "/publish").header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"commercialUseVerified\":false,\"translationVerified\":true,\"scoringVerified\":true}")).andExpect(jsonPath("$.code").value(400));
        var nc = scales.importScale(admin.id(), AssessmentFixtures.scale(unique(), "CC BY-NC-SA 4.0"));
        assertThatThrownBy(() -> scales.publishScale(admin.id(), nc.id(), new ScalePublishReqDTO(true, true, true))).isInstanceOf(RuntimeException.class);
    }
    // Separate committed synthetic fixtures let two independent database connections compete for real locks.
    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentStartsSavesAndSubmitsAreSerialized() throws Exception {
        UserVO admin = null, employee = null; AssessmentScaleVO scale = null;
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            admin = account("ADMIN"); employee = account("EMPLOYEE"); scale = published(admin);
            final Long userId = employee.id(), scaleId = scale.id();
            var gate = new java.util.concurrent.CountDownLatch(1);
            var starts = new ArrayList<java.util.concurrent.Future<AssessmentSessionVO>>();
            for (int i = 0; i < 2; i++) starts.add(executor.submit(() -> { gate.await(); return assessments.startAssessment(userId, new AssessmentStartReqDTO(scaleId, true)); }));
            gate.countDown();
            var draft = starts.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(starts.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS).id()).isEqualTo(draft.id());
            var saveGate = new java.util.concurrent.CountDownLatch(1);
            var saves = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                final int answer = i + 1;
                saves.add(executor.submit(() -> {
                    saveGate.await();
                    try { assessments.saveAnswers(userId, draft.id(), new AssessmentAnswersReqDTO(0, Map.of("EI1", answer))); return true; }
                    catch (com.personnel.common.exception.BusinessException expected) { return false; }
                }));
            }
            saveGate.countDown();
            assertThat(List.of(saves.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS), saves.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            var submitGate = new java.util.concurrent.CountDownLatch(1);
            var submissions = new ArrayList<java.util.concurrent.Future<AssessmentResultVO>>();
            for (int i = 0; i < 2; i++) submissions.add(executor.submit(() -> {
                submitGate.await(); return assessments.submitAssessment(userId, draft.id(), new AssessmentAnswersReqDTO(1, AssessmentFixtures.answers("ENTP")));
            }));
            submitGate.countDown();
            assertThat(submissions.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(submissions.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(assessments.listHistory(userId, userId, new AssessmentQueryReqDTO()).total()).isEqualTo(1);
        } finally {
            executor.shutdownNow(); executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
            // Exact IDs created by this test only; never truncate or remove existing business data.
            if (employee != null) jdbc.update("DELETE FROM operation_audit WHERE target_type IN ('USER','EMPLOYEE') AND target_id = ?", employee.id());
            if (admin != null) jdbc.update("DELETE FROM operation_audit WHERE target_type IN ('USER','EMPLOYEE') AND target_id = ?", admin.id());
            if (employee != null) { jdbc.update("DELETE FROM assessment_session WHERE user_id = ?", employee.id()); jdbc.update("DELETE FROM employee_profile WHERE user_id = ?", employee.id()); jdbc.update("DELETE FROM sys_user WHERE id = ?", employee.id()); }
            if (scale != null) jdbc.update("DELETE FROM assessment_scale WHERE id = ?", scale.id());
            if (admin != null) { jdbc.update("DELETE FROM employee_profile WHERE user_id = ?", admin.id()); jdbc.update("DELETE FROM sys_user WHERE id = ?", admin.id()); }
        }
    }
}
