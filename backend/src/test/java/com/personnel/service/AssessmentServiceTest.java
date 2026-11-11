package com.personnel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.AssessmentFixtures;
import com.personnel.common.util.AssessmentJson;
import com.personnel.dto.req.*;
import com.personnel.entity.AssessmentSessionDO;
import com.personnel.mapper.AssessmentSessionMapper;
import com.personnel.service.impl.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {
    @Mock AssessmentSessionMapper sessions;
    @Mock AssessmentScaleService scales;
    @Mock UserService users;
    final AssessmentJson json = new AssessmentJson(new ObjectMapper().findAndRegisterModules());
    final Clock clock = Clock.fixed(Instant.parse("2026-10-29T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    AssessmentService service;
    UserVO user(long id, String role) { return new UserVO(id, "test", "测试", null, role, "APPROVED", "ENABLED", null, null); }
    AssessmentSessionDO draft() {
        var value = new AssessmentSessionDO(); value.setId(5L); value.setUserId(1L); value.setScaleId(7L);
        value.setScaleSnapshot(json.write(AssessmentFixtures.definition())); value.setContentHash("test-hash");
        value.setAnswersJson("{}"); value.setStatus("DRAFT"); value.setRevision(0); return value;
    }
    @BeforeEach void setup() {
        var templates=mock(GrowthTemplateService.class);
        lenient().when(templates.getEffective()).thenReturn(com.personnel.convert.GrowthTemplateConvert.builtIn());
        service = new AssessmentServiceImpl(sessions, scales, new AssessmentScoringServiceImpl(), new AssessmentReportServiceImpl(mock(KnowledgeGraphService.class), new GrowthPlanServiceImpl(templates)), users, json, clock, mock(SystemSettingsService.class));
    }
    @Test void otherEmployeeAndManagerCannotReadSessionOrResult() {
        when(users.getById(2L)).thenReturn(user(2, "DEPARTMENT_MANAGER")); when(sessions.selectById(5L)).thenReturn(draft());
        assertThatThrownBy(() -> service.getSession(2L, 5L)).hasMessageContaining("无权访问");
        assertThatThrownBy(() -> service.getResult(2L, 5L)).hasMessageContaining("无权访问");
    }
    @Test void staleSaveDoesNotOverwriteAnswers() {
        when(users.getById(1L)).thenReturn(user(1, "EMPLOYEE")); var draft = draft(); draft.setRevision(2); when(sessions.selectForUpdate(5L)).thenReturn(draft);
        assertThatThrownBy(() -> service.saveAnswers(1L, 5L, new AssessmentAnswersReqDTO(0, Map.of("EI1", 4)))).hasMessageContaining("其他窗口");
        verify(sessions, never()).saveDraft(anyLong(), anyInt(), anyString(), any());
    }
    @Test void validDraftSaveReturnsNewRevisionAndServerTime() {
        when(users.getById(1L)).thenReturn(user(1, "EMPLOYEE")); when(sessions.selectForUpdate(5L)).thenReturn(draft());
        when(sessions.saveDraft(eq(5L), eq(0), anyString(), any())).thenReturn(1);
        var saved = service.saveAnswers(1L, 5L, new AssessmentAnswersReqDTO(0, Map.of("EI1", 4)));
        assertThat(saved.revision()).isEqualTo(1); assertThat(saved.answers()).containsEntry("EI1", 4);
        assertThat(saved.updatedAt()).isEqualTo(LocalDateTime.parse("2026-10-29T10:00:00"));
    }
    @Test void submissionIsIdempotentAndUsesFrozenDefinition() {
        when(users.getById(1L)).thenReturn(user(1, "EMPLOYEE")); var value = draft(); when(sessions.selectForUpdate(5L)).thenReturn(value);
        when(sessions.completeDraft(eq(5L), eq(0), anyString(), anyString(), any())).thenReturn(1);
        var result = service.submitAssessment(1L, 5L, new AssessmentAnswersReqDTO(0, AssessmentFixtures.answers("INTP")));
        assertThat(result.typeCode()).isEqualTo("INTP"); assertThat(result.contentHash()).isEqualTo("test-hash");
        value.setStatus("COMPLETED"); value.setResultJson(json.write(result));
        assertThat(service.submitAssessment(1L, 5L, new AssessmentAnswersReqDTO(0, Map.of()))).isEqualTo(result);
        verify(sessions, times(1)).completeDraft(anyLong(), anyInt(), anyString(), anyString(), any()); verifyNoInteractions(scales);
    }
    @Test void completionPreventsAnyFurtherDraftEdits() {
        when(users.getById(1L)).thenReturn(user(1, "EMPLOYEE")); var value = draft(); value.setStatus("COMPLETED"); when(sessions.selectForUpdate(5L)).thenReturn(value);
        assertThatThrownBy(() -> service.saveAnswers(1L, 5L, new AssessmentAnswersReqDTO(0, Map.of()))).hasMessageContaining("不可修改");
    }
    @Test void startingLocksBeforeConsistentReadsAndReusesExistingDraft() {
        when(users.getById(1L)).thenReturn(user(1, "EMPLOYEE")); when(sessions.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(draft());
        assertThat(service.startAssessment(1L, new AssessmentStartReqDTO(7L, true)).id()).isEqualTo(5);
        InOrder order = inOrder(users, sessions); order.verify(users).lockUser(1L); order.verify(users).getById(1L); order.verify(sessions).selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verifyNoInteractions(scales);
    }
    @Test void lackOfConsentDoesNotCreateDraft() {
        assertThatThrownBy(() -> service.startAssessment(1L, new AssessmentStartReqDTO(7L, false))).hasMessageContaining("同意"); verifyNoInteractions(sessions, scales, users);
    }
    @Test void managerCannotListOthersHistory() {
        when(users.getById(2L)).thenReturn(user(2, "DEPARTMENT_MANAGER"));
        assertThatThrownBy(() -> service.listHistory(2L, 1L, new AssessmentQueryReqDTO())).hasMessageContaining("无权"); verifyNoInteractions(sessions);
    }
}
