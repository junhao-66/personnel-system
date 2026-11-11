package com.personnel.convert;

import com.personnel.dto.req.UserRegisterReqDTO;
import com.personnel.entity.UserDO;
import com.personnel.vo.UserVO;

public final class UserConvert {

    private UserConvert() {}

    public static UserDO toDO(UserRegisterReqDTO source, String passwordHash) {
        UserDO target = new UserDO();
        target.setUserName(source.userName());
        target.setPasswordHash(passwordHash);
        target.setRealName(source.realName());
        target.setPhone(source.phone());
        return target;
    }

    public static UserVO toVO(UserDO source) {
        return new UserVO(
            source.getId(),
            source.getUserName(),
            source.getRealName(),
            source.getPhone(),
            source.getRoleCode(),
            source.getAuditStatus(),
            source.getUserStatus(),
            source.getDepartmentId(),
            source.getPositionId(),
            source.getAuditReason(),
            source.getReviewedAt(),
            source.getAuthVersion() == null ? 0L : source.getAuthVersion()
        );
    }
}
