package com.personnel.service;

import com.personnel.common.base.PageResult;
import com.personnel.dto.req.*;
import com.personnel.vo.*;

public interface AttendanceService {
    AttendanceRuleVO getRule();
    void updateRule(AttendanceRuleUpdateReqDTO request);
    TodayAttendanceVO getToday(Long userId);
    AttendanceRecordVO clockIn(Long userId);
    AttendanceRecordVO clockOut(Long userId);
    PageResult<AttendanceEmployeeVO> listMine(Long userId, AttendanceQueryReqDTO query);
    PageResult<AttendanceEmployeeVO> listAll(AdminAttendanceQueryReqDTO query);
}
