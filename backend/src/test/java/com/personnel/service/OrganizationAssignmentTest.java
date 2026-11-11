package com.personnel.service;

import com.personnel.common.exception.BusinessException;
import com.personnel.entity.DepartmentDO;
import com.personnel.entity.PositionDO;
import com.personnel.mapper.DepartmentMapper;
import com.personnel.mapper.PositionMapper;
import com.personnel.service.impl.OrganizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationAssignmentTest {
    @Mock DepartmentMapper departments;
    @Mock PositionMapper positions;
    OrganizationService service;
    @BeforeEach void setUp() { service = new OrganizationServiceImpl(departments, positions, mock(OperationAuditService.class)); }
    void department(int enabled) { var value = new DepartmentDO(); value.setId(1L); value.setIsEnabled(enabled); when(departments.selectById(1L)).thenReturn(value); }
    void position(Long departmentId, int enabled) { var value = new PositionDO(); value.setId(2L); value.setDepartmentId(departmentId); value.setIsEnabled(enabled); when(positions.selectById(2L)).thenReturn(value); }
    @Test void unassignedIsAllowed() { service.validateAssignment(null, null); verifyNoInteractions(departments, positions); }
    @Test void matchingAssignmentIsAllowed() { department(1); position(1L, 1); service.validateAssignment(1L, 2L); }
    @Test void sharedPositionIsAllowedWithoutDepartment() { position(null, 1); service.validateAssignment(null, 2L); }
    @Test void missingDepartmentIsRejected() { assertThatThrownBy(() -> service.validateAssignment(1L, null)).isInstanceOf(BusinessException.class); }
    @Test void disabledDepartmentIsRejected() { department(0); assertThatThrownBy(() -> service.validateAssignment(1L, null)).isInstanceOf(BusinessException.class); }
    @Test void missingPositionIsRejected() { assertThatThrownBy(() -> service.validateAssignment(null, 2L)).isInstanceOf(BusinessException.class); }
    @Test void disabledPositionIsRejected() { position(null, 0); assertThatThrownBy(() -> service.validateAssignment(null, 2L)).isInstanceOf(BusinessException.class); }
    @Test void departmentPositionCannotBeAssignedWithoutItsDepartment() { position(1L, 1); assertThatThrownBy(() -> service.validateAssignment(null, 2L)).hasMessage("岗位不属于所选部门"); }
    @Test void crossDepartmentPositionIsRejected() { department(1); position(3L, 1); assertThatThrownBy(() -> service.validateAssignment(1L, 2L)).hasMessage("岗位不属于所选部门"); }
}
