package com.personnel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.dto.req.TalentQueryReqDTO;
import com.personnel.entity.TalentEmployeeDO;
import com.personnel.entity.TalentGroupDO;
import com.personnel.mapper.TalentDashboardMapper;
import com.personnel.service.impl.TalentDashboardServiceImpl;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TalentDashboardServiceTest {
    @Mock TalentDashboardMapper mapper;
    @Mock UserService users;
    TalentDashboardService service;
    UserVO actor(String role, String audit, String status) { return new UserVO(1L, "test", "验收", null, role, audit, status, null, null); }
    TalentGroupDO group(Long departmentId, String type, long people, long assessments) {
        var value = new TalentGroupDO(); value.setDepartmentId(departmentId); value.setDepartmentName(departmentId == null ? "未分配部门" : "研发");
        value.setTypeCode(type); value.setEmployeeCount(people); value.setAssessmentCount(assessments); return value;
    }
    void admin() { when(users.getById(1L)).thenReturn(actor("ADMIN", "APPROVED", "ENABLED")); }
    @BeforeEach void setup() { service = new TalentDashboardServiceImpl(mapper, users, Clock.fixed(Instant.parse("2026-10-29T02:00:00Z"), ZoneId.of("Asia/Shanghai"))); }
    @Test void departmentAndTypeTotalsUseDistinctPeopleAndDifferentSubmissionDenominators() {
        admin(); when(mapper.selectGroups(any())).thenReturn(List.of(group(2L, "INTJ", 2, 5), group(2L, null, 1, 0), group(null, "INTJ", 1, 2)));
        var result = service.getDashboard(1L, new TalentQueryReqDTO());
        assertThat(result.employeeCount()).isEqualTo(4); assertThat(result.completedCount()).isEqualTo(3);
        assertThat(result.notCompletedCount()).isEqualTo(1); assertThat(result.assessmentCount()).isEqualTo(7);
        assertThat(result.completionRate()).isEqualByComparingTo("75.00");
        assertThat(result.typeDistribution()).hasSize(16);
        assertThat(result.typeDistribution().stream().filter(t -> t.typeCode().equals("INTJ")).findFirst().orElseThrow().percent()).isEqualByComparingTo("100");
        assertThat(result.departmentDistribution().get(0).completionRate()).isEqualByComparingTo("66.67");
        assertThat(result.departmentDistribution().get(1).departmentId()).isNull();
        assertThat(result.generatedAt()).isEqualTo(LocalDateTime.parse("2026-10-29T10:00:00"));
        verify(mapper, times(1)).selectGroups(any());
    }
    @Test void emptyScopeHasNoArtificialZeroPercentAndSixteenEmptyTypes() {
        admin(); when(mapper.selectGroups(any())).thenReturn(List.of()); var result = service.getDashboard(1L, new TalentQueryReqDTO());
        assertThat(result.completionRate()).isNull(); assertThat(result.employeeCount()).isZero(); assertThat(result.departmentDistribution()).isEmpty();
        assertThat(result.typeDistribution()).hasSize(16).allSatisfy(t -> { assertThat(t.employeeCount()).isZero(); assertThat(t.percent()).isNull(); });
    }
    @Test void uncompletedEmployeesHaveZeroCompletionButNoTypePercentDenominator() {
        admin(); when(mapper.selectGroups(any())).thenReturn(List.of(group(null, null, 3, 0)));
        var result = service.getDashboard(1L, new TalentQueryReqDTO());
        assertThat(result.completionRate()).isEqualByComparingTo("0"); assertThat(result.notCompletedCount()).isEqualTo(3);
        assertThat(result.typeDistribution()).allSatisfy(t -> assertThat(t.percent()).isNull());
    }
    @Test void allReadMethodsRejectNonAdminsBeforeQueryingStatistics() {
        for (var user : List.of(actor("EMPLOYEE", "APPROVED", "ENABLED"), actor("DEPARTMENT_MANAGER", "APPROVED", "ENABLED"), actor("ADMIN", "PENDING", "ENABLED"), actor("ADMIN", "APPROVED", "DISABLED"))) {
            when(users.getById(1L)).thenReturn(user);
            assertThatThrownBy(() -> service.getDashboard(1L, new TalentQueryReqDTO())).hasMessageContaining("管理员");
            assertThatThrownBy(() -> service.listEmployees(1L, new TalentQueryReqDTO())).hasMessageContaining("管理员");
            assertThatThrownBy(() -> service.getEmployee(1L, 2L)).hasMessageContaining("管理员");
        }
        verifyNoInteractions(mapper);
    }
    @Test void pageUsesBoundedRequestAndConvertsOnlyProjectionFields() {
        admin(); var query = new TalentQueryReqDTO(); query.setPageNum(2); query.setPageSize(1);
        var value = new TalentEmployeeDO(); value.setUserId(4L); value.setRealName("验收员工"); value.setAssessmentCount(0L);
        when(mapper.selectEmployeePage(any(), same(query))).thenAnswer(call -> {
            Page<TalentEmployeeDO> page = call.getArgument(0); assertThat(page.optimizeCountSql()).isFalse();
            page.setRecords(List.of(value)); page.setTotal(3); return page;
        });
        var result = service.listEmployees(1L, query);
        assertThat(result.pageNum()).isEqualTo(2); assertThat(result.pageSize()).isEqualTo(1); assertThat(result.total()).isEqualTo(3);
        assertThat(result.records().get(0).userId()).isEqualTo(4L); assertThat(result.records().get(0).latestResultId()).isNull();
    }
    @Test void missingEmployeeDoesNotReturnAnEmptyOrUnapprovedDetail() {
        admin(); when(mapper.selectEmployee(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.getEmployee(1L, 9L)).hasMessageContaining("尚未通过审核");
    }
}
