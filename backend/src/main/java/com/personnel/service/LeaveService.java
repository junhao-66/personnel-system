package com.personnel.service;

import com.personnel.common.base.PageResult;
import com.personnel.dto.req.*;
import com.personnel.vo.*;

public interface LeaveService {
    java.util.List<LeavePeriodVO> listApprovedPeriods(
        java.util.Collection<Long> userIds,
        java.time.LocalDateTime start,
        java.time.LocalDateTime end
    );
    void configureSelfApprover(Long departmentId, Long managerId);
    void configureManager(Long departmentId, Long managerId);
    LeaveOptionsVO getOptions(Long userId);
    LeaveVO addLeave(Long userId, LeaveCreateReqDTO request);
    PageResult<LeaveVO> listMine(Long userId, LeaveQueryReqDTO query);
    PageResult<LeaveVO> listForReview(Long userId, LeaveQueryReqDTO query);
    void reviewLeave(Long userId, Long id, LeaveReviewReqDTO request);
}
