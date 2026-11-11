package com.personnel.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** Read projection of accounts, profiles and latest completed assessments. Never returned by controllers. */
@Data
public class TalentEmployeeDO {

    private Long userId;
    private String userName;
    private String realName;
    private String roleCode;
    private String userStatus;
    private Long departmentId;
    private String departmentName;
    private Long positionId;
    private String positionName;
    private LocalDate hireDate;
    private String employmentStatus;
    private Long assessmentCount;
    private Long latestResultId;
    private String typeCode;
    private LocalDateTime submittedAt;
    private String scaleName;
    private String scaleVersion;
    private String scoringVersion;
}
