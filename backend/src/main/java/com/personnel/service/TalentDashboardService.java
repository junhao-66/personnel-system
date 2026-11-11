package com.personnel.service;

import com.personnel.common.base.PageResult;
import com.personnel.dto.req.TalentQueryReqDTO;
import com.personnel.vo.TalentDashboardVO;
import com.personnel.vo.TalentEmployeeVO;

public interface TalentDashboardService {
    TalentDashboardVO getDashboard(Long actorId, TalentQueryReqDTO query);
    PageResult<TalentEmployeeVO> listEmployees(Long actorId, TalentQueryReqDTO query);
    TalentEmployeeVO getEmployee(Long actorId, Long userId);
}
