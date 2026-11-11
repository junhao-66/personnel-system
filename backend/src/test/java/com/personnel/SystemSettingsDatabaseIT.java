package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(print = MockMvcPrint.NONE) @Transactional
class SystemSettingsDatabaseIT {
    @Autowired SystemSettingsService settings;
    @Autowired UserService users;
    @Autowired AssessmentService assessments;
    @Autowired AssessmentScaleService scales;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    UserVO user(String role) { return users.createUser(new AdminUserCreateReqDTO("set"+UUID.randomUUID().toString().replace("-", "").substring(0,16),"Fixture123","配置验收",null,role,null,null)); }
    String bearer(UserVO user) { return "Bearer "+jwt.createToken(user.id(),user.roleCode(),user.authVersion()); }
    SystemSettingsUpdateReqDTO request(boolean open, boolean announcement, int revision) { return new SystemSettingsUpdateReqDTO(open,"正在核校题库",announcement,"测评安排","已有草稿可继续完成",revision); }
    AssessmentScaleVO scale() { return scales.listPublished().stream().filter(s -> "PROJECT-DEMO-ONLY".equals(s.definition().license())).findFirst().orElseThrow(); }
    @Test void settingsCanBeReadByAuthenticatedEmployeesButWrittenOnlyByAdministrators() throws Exception {
        var owner=user("EMPLOYEE"); var manager=user("DEPARTMENT_MANAGER"); var admin=user("ADMIN");
        mvc.perform(get("/api/system/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/settings").header("Authorization",bearer(owner))).andExpect(jsonPath("$.data.assessmentEnabled").isBoolean());
        for (var actor : List.of(owner,manager)) {
            mvc.perform(get("/api/admin/system-settings").header("Authorization",bearer(actor))).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/system-settings").header("Authorization",bearer(actor)).contentType("application/json").content(json.writeValueAsBytes(request(false,true,settings.getCurrent().revision())))).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/admin/system-settings").header("Authorization",bearer(admin)).contentType("application/json").content(json.writeValueAsBytes(request(false,true,settings.getCurrent().revision()))))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.assessmentEnabled").value(false));
        mvc.perform(get("/api/admin/operation-audits").header("Authorization",bearer(admin)).param("targetType","SYSTEM_SETTINGS").param("operation","SYSTEM_SETTINGS_UPDATE"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.records[0].operation").value("SYSTEM_SETTINGS_UPDATE"));
    }
    @Test void closureBlocksOnlyNewDraftsAndExistingDraftCanStillSaveAndSubmit() {
        var owner=user("EMPLOYEE"); var newcomer=user("EMPLOYEE"); var admin=user("ADMIN"); var scale=scale();
        var draft=assessments.startAssessment(owner.id(),new AssessmentStartReqDTO(scale.id(),true));
        settings.updateSettings(admin.id(),request(false,true,settings.getCurrent().revision()));
        assertThatThrownBy(() -> assessments.startAssessment(newcomer.id(),new AssessmentStartReqDTO(scale.id(),true))).hasMessageContaining("暂未开放新测评");
        assertThat(assessments.startAssessment(owner.id(),new AssessmentStartReqDTO(scale.id(),true)).id()).isEqualTo(draft.id());
        var answers=new LinkedHashMap<String,Integer>(); for(var question:draft.scale().questions()) answers.put(question.key(),3);
        var saved=assessments.saveAnswers(owner.id(),draft.id(),new AssessmentAnswersReqDTO(draft.revision(),answers));
        var result=assessments.submitAssessment(owner.id(),draft.id(),new AssessmentAnswersReqDTO(saved.revision(),answers));
        assertThat(assessments.getResult(owner.id(),result.id())).isEqualTo(result);
        assertThatThrownBy(() -> assessments.startAssessment(owner.id(),new AssessmentStartReqDTO(scale.id(),true))).hasMessageContaining("暂未开放新测评");
        settings.updateSettings(admin.id(),request(true,false,settings.getCurrent().revision()));
        assertThat(assessments.startAssessment(newcomer.id(),new AssessmentStartReqDTO(scale.id(),true))).isNotNull();
    }
    @Test void oldRevisionCannotOverwriteAnotherAdministratorsChanges() {
        var first=user("ADMIN");var second=user("ADMIN");int revision=settings.getCurrent().revision();
        var updated=settings.updateSettings(first.id(),request(false,true,revision));
        assertThatThrownBy(() -> settings.updateSettings(second.id(),request(true,false,revision))).hasMessageContaining("其他管理员");
        assertThat(settings.getCurrent()).isEqualTo(updated);
    }
    @Test void missingPauseExplanationAndBlankPublishedAnnouncementsAreRejected() {
        var admin=user("ADMIN");var current=settings.getCurrent();
        for(var request:List.of(new SystemSettingsUpdateReqDTO(false," ",false,"","",current.revision()),new SystemSettingsUpdateReqDTO(true,"",true," ","content",current.revision()),new SystemSettingsUpdateReqDTO(true,"",true,"title"," ",current.revision()))) {
            assertThatThrownBy(() -> settings.updateSettings(admin.id(),request)).isInstanceOf(com.personnel.common.exception.BusinessException.class);
        }
        assertThat(settings.getCurrent()).isEqualTo(current);
    }
    @Test void hiddenAnnouncementRetainsTextAndUnchangedSaveDoesNotCreateNewRevision() {
        var admin=user("ADMIN");var saved=settings.updateSettings(admin.id(),request(true,false,settings.getCurrent().revision()));
        assertThat(saved.announcementContent()).isEqualTo("已有草稿可继续完成");
        assertThat(settings.getForEmployees().announcementContent()).isEmpty();
        assertThat(settings.getForEmployees().announcementTitle()).isEmpty();
        assertThat(settings.updateSettings(admin.id(),request(true,false,saved.revision()))).isEqualTo(saved);
        var published=settings.updateSettings(admin.id(),request(true,true,saved.revision()));
        assertThat(published.announcementEnabled()).isTrue();
        assertThat(jdbc.queryForObject("SELECT changed_fields FROM operation_audit WHERE operation='SYSTEM_SETTINGS_UPDATE' ORDER BY id DESC LIMIT 1",String.class)).contains("announcementEnabled").doesNotContain("已有草稿");
    }
    @Test void repeatedInitializationPreservesSavedSettings() {
        var admin=user("ADMIN");var saved=settings.updateSettings(admin.id(),request(false,true,settings.getCurrent().revision()));
        // Execute the repeatable INSERT only: MySQL DDL would implicitly commit this rollback-based test.
        jdbc.update("INSERT INTO sys_system_settings (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM sys_system_settings WHERE id=1)");
        assertThat(settings.getCurrent()).isEqualTo(saved);
    }
}
