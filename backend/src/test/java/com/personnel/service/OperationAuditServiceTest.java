package com.personnel.service;

import com.personnel.common.constant.AuditOperation;
import com.personnel.common.util.AuditActorContext;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.OperationAuditMapper;
import com.personnel.service.impl.OperationAuditServiceImpl;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationAuditServiceTest {
    OperationAuditMapper mapper = mock(OperationAuditMapper.class);
    OperationAuditService service = new OperationAuditServiceImpl(mapper,
            Clock.fixed(Instant.parse("2026-10-29T03:00:00Z"), ZoneId.of("Asia/Shanghai")));
    @AfterEach void cleanup() { AuditActorContext.clear(); }
    @Test void storesOnlyWhitelistedNamesAndVerifiedActorAtServerTime() {
        AuditActorContext.set(7L, "ADMIN");
        when(mapper.insert(any(OperationAuditDO.class))).thenReturn(1);
        service.append(AuditOperation.EMPLOYEE_UPDATE, 8L, List.of("phone", "phone", "realName"));
        var captor = org.mockito.ArgumentCaptor.forClass(OperationAuditDO.class);
        verify(mapper).insert(captor.capture());
        var value = captor.getValue();
        assertThat(value.getActorUserId()).isEqualTo(7L);
        assertThat(value.getActorRole()).isEqualTo("ADMIN");
        assertThat(value.getTargetId()).isEqualTo(8L);
        assertThat(value.getChangedFields()).isEqualTo("phone,realName");
        assertThat(value.getOccurredAt()).isEqualTo(LocalDateTime.of(2026,10,29,11,0));
    }
    @Test void rejectsRawValuesAndPasswordsBeforeDatabaseWrite() {
        assertThatThrownBy(() -> service.append(AuditOperation.USER_CREATE, 8L, List.of("passwordHash"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.append(AuditOperation.EMPLOYEE_UPDATE, 8L, List.of("13800138000"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }
    @Test void noChangedFieldsDoesNotInventMutation() {
        service.append(AuditOperation.EMPLOYEE_UPDATE, 8L, List.of()); verifyNoInteractions(mapper);
    }
    @Test void internalCallIsExplicitlyMarkedAsSystem() {
        when(mapper.insert(any(OperationAuditDO.class))).thenReturn(1);
        service.append(AuditOperation.USER_AUDIT, 8L, List.of("auditStatus"));
        verify(mapper).insert(argThat((OperationAuditDO value) -> value.getActorUserId() == null && value.getActorRole().equals("SYSTEM")));
    }
    @Test void insertionFailureCannotBeSilentlyIgnored() {
        assertThatThrownBy(() -> service.append(AuditOperation.USER_AUDIT, 8L, List.of("auditStatus"))).hasMessageContaining("保存失败");
    }
}
