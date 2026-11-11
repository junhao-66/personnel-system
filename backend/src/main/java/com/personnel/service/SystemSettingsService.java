package com.personnel.service;

import com.personnel.dto.req.SystemSettingsUpdateReqDTO;
import com.personnel.vo.SystemSettingsVO;

public interface SystemSettingsService {
    SystemSettingsVO getCurrent();
    SystemSettingsVO getForEmployees();
    SystemSettingsVO updateSettings(Long actorId, SystemSettingsUpdateReqDTO request);
    void requireAssessmentOpen();
}
