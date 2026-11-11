package com.personnel.service;

import com.personnel.common.base.PageResult;
import com.personnel.dto.req.EmployeeQueryReqDTO;
import com.personnel.dto.req.EmployeeUpdateReqDTO;
import com.personnel.vo.EmployeeVO;

public interface EmployeeService {
    PageResult<EmployeeVO> listEmployees(EmployeeQueryReqDTO query);
    void updateEmployee(Long id, EmployeeUpdateReqDTO request);
}
