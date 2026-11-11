package com.personnel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.AssessmentFixtures;
import com.personnel.common.util.AssessmentJson;
import com.personnel.convert.AssessmentConvert;
import com.personnel.dto.req.ScalePublishReqDTO;
import com.personnel.entity.AssessmentScaleDO;
import com.personnel.mapper.AssessmentScaleMapper;
import com.personnel.service.impl.AssessmentScaleServiceImpl;
import com.personnel.vo.UserVO;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentScaleServiceTest {
    @Mock AssessmentScaleMapper scales;
    @Mock UserService users;
    @Mock OperationAuditService audit;
    static ValidatorFactory validation;
    AssessmentScaleService service;
    AssessmentJson json = new AssessmentJson(new ObjectMapper().findAndRegisterModules());
    @BeforeAll static void beforeAll() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void afterAll() { validation.close(); }
    @BeforeEach void setup() {
        service = new AssessmentScaleServiceImpl(scales, users, json, validation.getValidator(), Clock.system(ZoneId.of("Asia/Shanghai")), audit);
        when(users.getById(1L)).thenReturn(new UserVO(1L, "admin", "测试", null, "ADMIN", "APPROVED", "ENABLED", null, null));
    }
    @Test void nonCommercialLicenseCannotPublishAsEnterpriseInstrument() {
        var value = new AssessmentScaleDO(); value.setDefinitionJson(json.write(AssessmentConvert.toDefinition(AssessmentFixtures.scale("test-v1", "CC BY-NC-SA 4.0")))); when(scales.selectById(7L)).thenReturn(value);
        assertThatThrownBy(() -> service.publishScale(1L, 7L, new ScalePublishReqDTO(true, true, true))).hasMessageContaining("非商业");
        verify(scales, never()).publishDraft(anyLong(), anyLong(), any());
    }
    @Test void publishingRequiresAllThreeAttestations() {
        assertThatThrownBy(() -> service.publishScale(1L, 7L, new ScalePublishReqDTO(true, false, true))).hasMessageContaining("必须核验"); verifyNoInteractions(scales);
    }
    @Test void importingCreatesDraftWithStableHashAndActor() {
        doAnswer(call -> { ((AssessmentScaleDO) call.getArgument(0)).setId(7L); return 1; }).when(scales).insert(any(AssessmentScaleDO.class));
        var value = service.importScale(1L, AssessmentFixtures.scale("test-v1", "ORIGINAL-TEST-FIXTURE"));
        assertThat(value.status()).isEqualTo("DRAFT"); assertThat(value.contentHash()).matches("[0-9a-f]{64}");
        var captor = ArgumentCaptor.forClass(AssessmentScaleDO.class); verify(scales).insert(captor.capture()); assertThat(captor.getValue().getCreateBy()).isEqualTo(1);
    }
    @Test void nonAdminCannotImport() {
        when(users.getById(1L)).thenReturn(new UserVO(1L, "staff", "测试", null, "EMPLOYEE", "APPROVED", "ENABLED", null, null));
        assertThatThrownBy(() -> service.importScale(1L, AssessmentFixtures.scale("test-v1", "ORIGINAL-TEST-FIXTURE"))).hasMessageContaining("仅有效管理员"); verifyNoInteractions(scales);
    }
}
