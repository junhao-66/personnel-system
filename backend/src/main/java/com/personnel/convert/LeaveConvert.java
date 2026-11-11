package com.personnel.convert;

import com.personnel.dto.req.LeaveCreateReqDTO;
import com.personnel.entity.LeaveRequestDO;
import com.personnel.vo.LeaveVO;
import java.time.LocalDateTime;

public final class LeaveConvert {

    private LeaveConvert() {}

    public static LeaveRequestDO toDO(
        Long userId,
        Long departmentId,
        LeaveCreateReqDTO request,
        LocalDateTime now
    ) {
        var value = new LeaveRequestDO();
        value.setEnterpriseId(1L);
        value.setUserId(userId);
        value.setDepartmentId(departmentId);
        value.setLeaveType(request.leaveType());
        value.setStartTime(request.startTime());
        value.setEndTime(request.endTime());
        value.setReason(request.reason().trim());
        value.setStatus("PENDING");
        value.setCreateTime(now);
        value.setUpdateTime(now);
        return value;
    }

    public static LeaveVO toVO(LeaveRequestDO value, String realName) {
        return new LeaveVO(
            value.getId(),
            value.getUserId(),
            realName,
            value.getDepartmentId(),
            value.getLeaveType(),
            value.getStartTime(),
            value.getEndTime(),
            value.getReason(),
            value.getStatus(),
            value.getReviewerId(),
            value.getReviewComment(),
            value.getReviewedAt(),
            value.getCreateTime()
        );
    }
}
