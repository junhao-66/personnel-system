package com.personnel.service;

import com.personnel.common.base.PageResult;
import com.personnel.dto.req.*;
import com.personnel.vo.*;

public interface AssessmentService {
    AssessmentSessionVO getCurrent(Long userId);
    AssessmentSessionVO startAssessment(Long userId, AssessmentStartReqDTO request);
    AssessmentSessionVO getSession(Long userId, Long id);
    AssessmentSessionVO saveAnswers(Long userId, Long id, AssessmentAnswersReqDTO request);
    AssessmentResultVO submitAssessment(Long userId, Long id, AssessmentAnswersReqDTO request);
    AssessmentResultVO getResult(Long userId, Long id);
    PageResult<AssessmentHistoryVO> listHistory(
        Long actorId,
        Long ownerId,
        AssessmentQueryReqDTO query
    );
    AssessmentComparisonVO compareResults(Long userId, Long firstId, Long secondId);
}
