package com.personnel.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TalentEmployeeVO(
    Long userId,
    String userName,
    String realName,
    String roleCode,
    String userStatus,
    Long departmentId,
    String departmentName,
    Long positionId,
    String positionName,
    LocalDate hireDate,
    String employmentStatus,
    long assessmentCount,
    Long latestResultId,
    String typeCode,
    LocalDateTime submittedAt,
    String scaleName,
    String scaleVersion,
    String scoringVersion
) {}
