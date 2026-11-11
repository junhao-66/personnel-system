package com.personnel.service;

import com.personnel.common.exception.BusinessException;
import com.personnel.dto.req.*;
import com.personnel.entity.LeaveRequestDO;
import com.personnel.mapper.LeaveRequestMapper;
import com.personnel.service.impl.LeaveServiceImpl;
import com.personnel.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {
    @Mock LeaveRequestMapper records;
    @Mock UserService users;
    @Mock OrganizationService organizations;
    @Mock OperationAuditService audit;
    LeaveServiceImpl service;
    final LocalDateTime start = LocalDateTime.of(2026, 11, 2, 9, 0);
    @BeforeEach void setup() {
        service = new LeaveServiceImpl(records, users, organizations, audit,
                Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneId.of("Asia/Shanghai")), List.of("事假", "病假"), 30, true);
    }
    UserVO user(long id, String role) { return new UserVO(id, "test" + id, "测试", null, role, "APPROVED", "ENABLED", 1L, null); }
    void applicant() {
        when(users.getById(1L)).thenReturn(user(1, "EMPLOYEE"));
        when(organizations.listDepartments()).thenReturn(List.of(new DepartmentVO(1L, "研发", "DEV", 2L, 0, 1)));
        when(users.getById(2L)).thenReturn(user(2, "DEPARTMENT_MANAGER"));
    }
    LeaveCreateReqDTO request(LocalDateTime end) { return new LeaveCreateReqDTO("事假", start, end, "个人事务"); }
    @Test void createsPendingWithServerIdentityAndLocksBeforeOverlapCheck() {
        applicant();
        doAnswer(invocation -> { ((LeaveRequestDO) invocation.getArgument(0)).setId(7L); return 1; }).when(records).insert(any(LeaveRequestDO.class));
        var result = service.addLeave(1L, request(start.plusHours(1)));
        assertThat(result.userId()).isEqualTo(1); assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.createTime()).isEqualTo(LocalDateTime.parse("2026-10-28T09:00:00"));
        InOrder order = inOrder(users, records); order.verify(users).lockUser(1L); order.verify(users).getById(1L);
        order.verify(users).getById(2L); order.verify(records).countOverlapping(1L, start, start.plusHours(1));
    }
    @Test void overlappingPendingOrApprovedIsBlocked() {
        applicant(); when(records.countOverlapping(anyLong(), any(), any())).thenReturn(1L);
        assertThatThrownBy(() -> service.addLeave(1L, request(start.plusHours(1)))).isInstanceOf(BusinessException.class).hasMessageContaining("重叠");
        verify(records, never()).insert(any(LeaveRequestDO.class));
    }
    @Test void invalidOrderAndMinimumUnitAreBlocked() {
        applicant();
        assertThatThrownBy(() -> service.addLeave(1L, request(start))).hasMessageContaining("结束时间");
        assertThatThrownBy(() -> service.addLeave(1L, request(start.plusMinutes(31)))).hasMessageContaining("整数倍");
        verify(records, never()).countOverlapping(anyLong(), any(), any());
    }
    @Test void pastStartAndSecondPrecisionAreBlocked() {
        applicant();
        assertThatThrownBy(() -> service.addLeave(1L, new LeaveCreateReqDTO("事假", start.minusYears(1), start, "原因"))).hasMessageContaining("服务器时间");
        assertThatThrownBy(() -> service.addLeave(1L, new LeaveCreateReqDTO("事假", start.plusSeconds(1), start.plusHours(1), "原因"))).hasMessageContaining("分钟");
    }
    @Test void selfApprovalAndMissingDepartmentReturnUnavailableOptions() {
        when(users.getById(1L)).thenReturn(user(1, "DEPARTMENT_MANAGER"));
        when(organizations.listDepartments()).thenReturn(List.of(new DepartmentVO(1L, "研发", "DEV", 1L, 0, 1)));
        assertThat(service.getOptions(1L).unavailableReason()).contains("本人不能自批");
        when(organizations.listDepartments()).thenReturn(List.of());
        assertThat(service.getOptions(1L).unavailableReason()).contains("所属部门");
    }
    @Test void rejectedRequiresNonBlankReason() {
        when(users.getById(2L)).thenReturn(user(2, "DEPARTMENT_MANAGER"));
        assertThatThrownBy(() -> service.reviewLeave(2L, 7L, new LeaveReviewReqDTO("REJECTED", "  "))).hasMessageContaining("拒绝必须");
        verifyNoInteractions(records);
    }
    @Test void employeeCannotReview() {
        when(users.getById(1L)).thenReturn(user(1, "EMPLOYEE"));
        assertThatThrownBy(() -> service.reviewLeave(1L, 7L, new LeaveReviewReqDTO("APPROVED", ""))).hasMessageContaining("仅部门负责人");
        verifyNoInteractions(records);
    }
    @Test void changedScopeOrRepeatedReviewCannotOverwriteResult() {
        when(users.getById(2L)).thenReturn(user(2, "DEPARTMENT_MANAGER"));
        assertThatThrownBy(() -> service.reviewLeave(2L, 7L, new LeaveReviewReqDTO("APPROVED", ""))).hasMessageContaining("审批范围");
    }
    @Test void successfulReviewUsesConditionalUpdateAndTrimmedComment() {
        when(users.getById(2L)).thenReturn(user(2, "DEPARTMENT_MANAGER"));
        when(records.reviewPending(eq(7L), eq(2L), eq("REJECTED"), eq("补充材料"), any())).thenReturn(1);
        service.reviewLeave(2L, 7L, new LeaveReviewReqDTO("REJECTED", " 补充材料 "));
        verify(records).reviewPending(7L, 2L, "REJECTED", "补充材料", LocalDateTime.parse("2026-10-28T09:00:00"));
    }
    @Test void unapprovedUserCannotSubmit() {
        when(users.getById(1L)).thenReturn(new UserVO(1L, "pending", "待审核", null, "EMPLOYEE", "PENDING", "ENABLED", 1L, null));
        assertThatThrownBy(() -> service.addLeave(1L, request(start.plusHours(1)))).hasMessageContaining("未审核");
        verifyNoInteractions(records);
    }
    @Test void crossDayCanBeDisabledByConfiguration() {
        applicant();
        var restricted = new LeaveServiceImpl(records, users, organizations, audit, Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneId.of("Asia/Shanghai")), List.of("事假"), 30, false);
        assertThatThrownBy(() -> restricted.addLeave(1L, request(start.plusDays(1)))).hasMessageContaining("跨日");
    }
}
