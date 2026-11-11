package com.personnel.service;

import com.personnel.vo.*;

public interface AiAssistantService {
    AiStatusVO status(Long actor);
    AiAnswerVO expandReport(Long actor, Long resultId);
    AiAnswerVO askEmployee(Long actor, Long owner, String question);
    AiAnswerVO askDepartment(Long actor, Long departmentId, String question);
}
