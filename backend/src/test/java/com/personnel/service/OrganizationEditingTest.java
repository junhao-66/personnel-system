package com.personnel.service;

import com.personnel.common.constant.AuditOperation;
import com.personnel.dto.req.DepartmentSaveReqDTO;
import com.personnel.dto.req.PositionSaveReqDTO;
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
class OrganizationEditingTest {
    @Mock DepartmentMapper departments;
    @Mock PositionMapper positions;
    @Mock OperationAuditService audit;
    OrganizationService service;
    @BeforeEach void setup() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, DepartmentDO.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PositionDO.class);
        service = new OrganizationServiceImpl(departments, positions, audit);
    }
    DepartmentDO department() {
        var value = new DepartmentDO(); value.setId(1L); value.setDepartmentName("部门");
        value.setDepartmentCode("D"); value.setSortOrder(0); value.setManagerUserId(9L); value.setIsEnabled(0);
        when(departments.selectById(1L)).thenReturn(value); return value;
    }
    PositionDO position() {
        var value = new PositionDO(); value.setId(2L); value.setPositionName("岗位"); value.setPositionCode("P");
        value.setDepartmentId(1L); value.setDescription("旧说明"); value.setIsEnabled(0);
        when(positions.selectById(2L)).thenReturn(value); return value;
    }
    @Test void departmentEditPreservesManagerAndStatus() {
        department(); var result = service.updateDepartment(1L, new DepartmentSaveReqDTO(" 新部门 ", " D2 ", 99L, 3));
        assertThat(result.departmentName()).isEqualTo("新部门");
        assertThat(result.managerUserId()).isEqualTo(9L); assertThat(result.isEnabled()).isZero();
        verify(audit).append(eq(AuditOperation.DEPARTMENT_UPDATE), eq(1L), anyList());
    }
    @Test void identicalDepartmentDoesNotWriteAudit() {
        department(); service.updateDepartment(1L, new DepartmentSaveReqDTO("部门", "D", null, 0));
        verifyNoInteractions(audit); verify(departments, never()).update(isNull(), any());
    }
    @Test void duplicateDepartmentRejected() {
        department(); when(departments.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.updateDepartment(1L, new DepartmentSaveReqDTO("新", "D2", null, 0))).hasMessage("部门编码已存在");
        verifyNoInteractions(audit);
    }
    @Test void missingDepartmentRejected() {
        assertThatThrownBy(() -> service.updateDepartment(1L, new DepartmentSaveReqDTO("新", "D", null, 0))).hasMessage("部门不存在");
    }
    @Test void negativeSortRejected() {
        department(); assertThatThrownBy(() -> service.updateDepartment(1L, new DepartmentSaveReqDTO("新", "D", null, -1))).hasMessage("排序不能小于0");
    }
    @Test void positionEditPreservesAssociationAndCanClearDescription() {
        position(); var result = service.updatePosition(2L, new PositionSaveReqDTO("新岗位", "P2", 1L, ""));
        assertThat(result.departmentId()).isEqualTo(1L); assertThat(result.description()).isEmpty(); assertThat(result.isEnabled()).isZero();
        verify(audit).append(eq(AuditOperation.POSITION_UPDATE), eq(2L), anyList());
    }
    @Test void crossDepartmentEditRejected() {
        position(); assertThatThrownBy(() -> service.updatePosition(2L, new PositionSaveReqDTO("岗位", "P", 3L, ""))).hasMessageContaining("不能变更");
        verifyNoInteractions(audit); verify(positions, never()).update(isNull(), any());
    }
    @Test void duplicatePositionRejected() {
        position(); when(positions.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.updatePosition(2L, new PositionSaveReqDTO("岗位", "P2", 1L, ""))).hasMessage("岗位编码已存在");
    }
}
