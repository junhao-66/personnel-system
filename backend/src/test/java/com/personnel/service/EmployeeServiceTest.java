package com.personnel.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.exception.BusinessException;
import com.personnel.dto.req.EmployeeQueryReqDTO;
import com.personnel.dto.req.EmployeeUpdateReqDTO;
import com.personnel.entity.EmployeeProfileDO;
import com.personnel.entity.UserDO;
import com.personnel.mapper.EmployeeProfileMapper;
import com.personnel.mapper.UserMapper;
import com.personnel.service.impl.EmployeeServiceImpl;
import com.personnel.vo.DepartmentVO;
import com.personnel.vo.PositionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
    @Mock UserMapper userMapper;
    @Mock EmployeeProfileMapper profileMapper;
    @Mock OrganizationService organizationService;
    @Captor ArgumentCaptor<Wrapper<UserDO>> userUpdate;
    EmployeeService service;

    @BeforeEach
    void setUp() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, UserDO.class);
        TableInfoHelper.initTableInfo(assistant, EmployeeProfileDO.class);
        service = new EmployeeServiceImpl(userMapper, profileMapper, organizationService, mock(OperationAuditService.class));
    }

    UserDO approvedUser() {
        UserDO user = new UserDO();
        user.setId(9L); user.setRealName("员工"); user.setUserName("employee");
        user.setAuditStatus("APPROVED"); user.setDepartmentId(1L); user.setPositionId(2L);
        return user;
    }

    EmployeeUpdateReqDTO request(Long departmentId, Long positionId) {
        return new EmployeeUpdateReqDTO(" 新姓名 ", "", departmentId, positionId, null, "RESIGNED");
    }

    @Test
    void emptyPagePreservesPaginationAndDoesNotLoadDictionaries() {
        Page<UserDO> page = new Page<>(3, 10, 12);
        when(userMapper.selectEmployeePage(any(), any(), any(), any())).thenReturn(page);
        EmployeeQueryReqDTO query = new EmployeeQueryReqDTO(); query.setPageNum(3);
        var result = service.listEmployees(query);
        assertThat(result.records()).isEmpty(); assertThat(result.total()).isEqualTo(12);
        assertThat(result.pageNum()).isEqualTo(3);
        verifyNoInteractions(profileMapper, organizationService);
    }

    @Test
    void pageCombinesNamesProfilesAndTrimsKeyword() {
        var user = approvedUser();
        var profile = new EmployeeProfileDO(); profile.setUserId(9L); profile.setEmploymentStatus("RESIGNED");
        profile.setHireDate(LocalDate.of(2025, 1, 1));
        Page<UserDO> page = new Page<>(1, 10, 1); page.setRecords(List.of(user));
        when(userMapper.selectEmployeePage(any(), eq("员工"), eq(1L), eq("RESIGNED"))).thenReturn(page);
        when(profileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(profile));
        when(organizationService.listDepartments()).thenReturn(List.of(new DepartmentVO(1L, "研发部", "DEV", null, 0, 1)));
        when(organizationService.listPositions(null)).thenReturn(List.of(new PositionVO(2L, "开发", "DEV", 1L, null, 1)));
        var query = new EmployeeQueryReqDTO(); query.setKeyword(" 员工 "); query.setDepartmentId(1L); query.setEmploymentStatus("RESIGNED");
        var result = service.listEmployees(query).records().get(0);
        assertThat(result.departmentName()).isEqualTo("研发部"); assertThat(result.positionName()).isEqualTo("开发");
        assertThat(result.employmentStatus()).isEqualTo("RESIGNED"); assertThat(result.hireDate()).isEqualTo(profile.getHireDate());
        verify(profileMapper, times(1)).selectBatchIds(anyCollection());
    }

    @Test
    void legacyAccountWithoutProfileStillAppears() {
        var user = approvedUser(); user.setDepartmentId(null); user.setPositionId(null);
        Page<UserDO> page = new Page<>(1, 10, 1); page.setRecords(List.of(user));
        when(userMapper.selectEmployeePage(any(), any(), any(), any())).thenReturn(page);
        when(profileMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(organizationService.listDepartments()).thenReturn(List.of());
        when(organizationService.listPositions(null)).thenReturn(List.of());
        var result = service.listEmployees(new EmployeeQueryReqDTO()).records().get(0);
        assertThat(result.employmentStatus()).isEqualTo("ACTIVE"); assertThat(result.departmentName()).isNull();
    }

    @Test
    void missingEmployeeCannotBeUpdated() {
        assertThatThrownBy(() -> service.updateEmployee(9L, request(null, null))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(profileMapper, organizationService);
    }

    @Test
    void pendingEmployeeCannotBeUpdated() {
        var user = approvedUser(); user.setAuditStatus("PENDING"); when(userMapper.selectById(9L)).thenReturn(user);
        assertThatThrownBy(() -> service.updateEmployee(9L, request(null, null))).isInstanceOf(BusinessException.class);
        verify(userMapper, never()).update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<UserDO>>any());
    }

    @Test
    void invalidAssignmentStopsBeforeAnyWrite() {
        when(userMapper.selectById(9L)).thenReturn(approvedUser());
        doThrow(new BusinessException("岗位不属于所选部门")).when(organizationService).validateAssignment(3L, 2L);
        assertThatThrownBy(() -> service.updateEmployee(9L, request(3L, 2L))).hasMessage("岗位不属于所选部门");
        verify(userMapper, never()).update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<UserDO>>any()); verifyNoInteractions(profileMapper);
    }

    @Test
    void unchangedHistoricalAssignmentCanBePreservedAndProfileCreated() {
        when(userMapper.selectById(9L)).thenReturn(approvedUser());
        service.updateEmployee(9L, request(1L, 2L));
        verifyNoInteractions(organizationService);
        var captor = ArgumentCaptor.forClass(EmployeeProfileDO.class);
        verify(profileMapper).insert(captor.capture());
        assertThat(captor.getValue().getEmploymentStatus()).isEqualTo("RESIGNED");
    }

    @Test
    void nullAssignmentsAndHireDateAreWrittenExplicitly() {
        when(userMapper.selectById(9L)).thenReturn(approvedUser());
        EmployeeProfileDO profile = new EmployeeProfileDO(); profile.setUserId(9L);
        when(profileMapper.selectById(9L)).thenReturn(profile);
        service.updateEmployee(9L, request(null, null));
        verify(organizationService).validateAssignment(null, null);
        verify(userMapper).update(isNull(), userUpdate.capture());
        assertThat(userUpdate.getValue().getSqlSet()).contains("department_id=", "position_id=");
        var parameters = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) userUpdate.getValue()).getParamNameValuePairs();
        assertThat(parameters).containsValue(null).containsValue("新姓名");
        verify(profileMapper).update(isNull(), argThat(wrapper -> wrapper.getSqlSet().contains("hire_date=")));
        verify(profileMapper, never()).insert(any(EmployeeProfileDO.class));
    }
}
