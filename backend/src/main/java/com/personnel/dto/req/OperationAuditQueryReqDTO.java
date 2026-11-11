package com.personnel.dto.req;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class OperationAuditQueryReqDTO {

    @NotNull
    @Min(1)
    @Max(1000000)
    private Integer pageNum = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    @Positive
    private Long actorUserId;

    @Positive
    private Long targetId;

    @Pattern(
        regexp = "USER|EMPLOYEE|DEPARTMENT|POSITION|LEAVE|SCALE|GRAPH|ATTENDANCE_RULE|GROWTH_TEMPLATE|JOB_PROFILE|SYSTEM_SETTINGS"
    )
    private String targetType;

    @Pattern(
        regexp = "USER_CONTACT|USER_PASSWORD_CHANGE|SYSTEM_SETTINGS_UPDATE|USER_CREATE|USER_AUDIT|USER_ACCESS|EMPLOYEE_UPDATE|DEPARTMENT_CREATE|DEPARTMENT_UPDATE|DEPARTMENT_STATUS|DEPARTMENT_MANAGER|DEPARTMENT_SELF_APPROVER|POSITION_CREATE|POSITION_UPDATE|POSITION_STATUS|LEAVE_CREATE|LEAVE_REVIEW|SCALE_IMPORT|SCALE_PUBLISH|SCALE_ARCHIVE|GRAPH_IMPORT|GRAPH_PUBLISH|GRAPH_ARCHIVE|ATTENDANCE_RULE_UPDATE|TEMPLATE_CREATE|TEMPLATE_PUBLISH|TEMPLATE_ARCHIVE|JOB_PROFILE_CREATE|JOB_PROFILE_PUBLISH|JOB_PROFILE_ARCHIVE"
    )
    private String operation;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
