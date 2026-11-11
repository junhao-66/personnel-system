package com.personnel.service;

import com.personnel.dto.req.DepartmentSaveReqDTO;
import com.personnel.dto.req.PositionSaveReqDTO;
import com.personnel.vo.DepartmentVO;
import com.personnel.vo.PositionVO;
import java.util.List;

public interface OrganizationService {
    void updateDepartmentSelfApprover(Long departmentId, Long managerId);
    void updateDepartmentManager(Long departmentId, Long managerId);
    void validateAssignment(Long departmentId, Long positionId);
    List<DepartmentVO> listDepartments();
    DepartmentVO addDepartment(DepartmentSaveReqDTO request);
    DepartmentVO updateDepartment(Long id, DepartmentSaveReqDTO request);
    void setDepartmentEnabled(Long id, boolean enabled);
    List<PositionVO> listPositions(Long departmentId);
    PositionVO addPosition(PositionSaveReqDTO request);
    PositionVO updatePosition(Long id, PositionSaveReqDTO request);
    void setPositionEnabled(Long id, boolean enabled);
}
