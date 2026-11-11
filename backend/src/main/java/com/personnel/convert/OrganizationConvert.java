package com.personnel.convert;

import com.personnel.entity.DepartmentDO;
import com.personnel.entity.PositionDO;
import com.personnel.vo.DepartmentVO;
import com.personnel.vo.PositionVO;

public final class OrganizationConvert {

    private OrganizationConvert() {}

    public static DepartmentVO toDepartmentVO(DepartmentDO value) {
        return new DepartmentVO(
            value.getId(),
            value.getDepartmentName(),
            value.getDepartmentCode(),
            value.getManagerUserId(),
            value.getSortOrder(),
            value.getIsEnabled(),
            value.getSelfApproverUserId()
        );
    }

    public static PositionVO toPositionVO(PositionDO value) {
        return new PositionVO(
            value.getId(),
            value.getPositionName(),
            value.getPositionCode(),
            value.getDepartmentId(),
            value.getDescription(),
            value.getIsEnabled()
        );
    }
}
