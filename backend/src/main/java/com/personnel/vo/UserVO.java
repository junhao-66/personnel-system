package com.personnel.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

public record UserVO(
    Long id,
    String userName,
    String realName,
    String phone,
    String roleCode,
    String auditStatus,
    String userStatus,
    Long departmentId,
    Long positionId,
    String auditReason,
    LocalDateTime reviewedAt,
    @JsonIgnore long authVersion
) {
    public UserVO(
        Long id,
        String userName,
        String realName,
        String phone,
        String roleCode,
        String auditStatus,
        String userStatus,
        Long departmentId,
        Long positionId
    ) {
        this(
            id,
            userName,
            realName,
            phone,
            roleCode,
            auditStatus,
            userStatus,
            departmentId,
            positionId,
            null,
            null,
            0L
        );
    }
}
