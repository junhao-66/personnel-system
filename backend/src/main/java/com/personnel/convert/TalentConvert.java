package com.personnel.convert;

import com.personnel.entity.TalentEmployeeDO;
import com.personnel.vo.TalentEmployeeVO;

public final class TalentConvert {

    private TalentConvert() {}

    public static TalentEmployeeVO toVO(TalentEmployeeDO value) {
        return new TalentEmployeeVO(
            value.getUserId(),
            value.getUserName(),
            value.getRealName(),
            value.getRoleCode(),
            value.getUserStatus(),
            value.getDepartmentId(),
            value.getDepartmentName(),
            value.getPositionId(),
            value.getPositionName(),
            value.getHireDate(),
            value.getEmploymentStatus(),
            value.getAssessmentCount(),
            value.getLatestResultId(),
            value.getTypeCode(),
            value.getSubmittedAt(),
            value.getScaleName(),
            value.getScaleVersion(),
            value.getScoringVersion()
        );
    }
}
