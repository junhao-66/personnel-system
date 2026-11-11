package com.personnel.service;

import com.personnel.vo.TeamAnalysisVO;

public interface TeamAnalysisService {
    TeamAnalysisVO analyzeDepartment(Long actor, Long departmentId);
}
