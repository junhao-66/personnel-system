package com.personnel.convert;

import com.personnel.dto.req.EmployeeUpdateReqDTO;
import com.personnel.entity.EmployeeProfileDO;
import com.personnel.entity.UserDO;
import com.personnel.vo.EmployeeVO;

public final class EmployeeConvert {

    private EmployeeConvert() {}

    public static EmployeeVO toVO(
        UserDO user,
        EmployeeProfileDO profile,
        String departmentName,
        String positionName
    ) {
        return new EmployeeVO(
            user.getId(),
            user.getUserName(),
            user.getRealName(),
            user.getPhone(),
            user.getRoleCode(),
            user.getDepartmentId(),
            departmentName,
            user.getPositionId(),
            positionName,
            profile == null ? null : profile.getHireDate(),
            profile == null ? "ACTIVE" : profile.getEmploymentStatus(),
            user.getUserStatus()
        );
    }

    public static EmployeeProfileDO toProfile(Long userId, EmployeeUpdateReqDTO request) {
        EmployeeProfileDO profile = new EmployeeProfileDO();
        profile.setUserId(userId);
        profile.setHireDate(request.hireDate());
        profile.setEmploymentStatus(request.employmentStatus());
        return profile;
    }
}
