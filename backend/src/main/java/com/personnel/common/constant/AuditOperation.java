package com.personnel.common.constant;

import java.util.Set;

/** Only field names from these whitelists may enter audit storage. */
public enum AuditOperation {
    USER_CONTACT("USER", Set.of("phone")),
    USER_PASSWORD_CHANGE("USER", Set.of("credentialUpdated")),
    SYSTEM_SETTINGS_UPDATE(
        "SYSTEM_SETTINGS",
        Set.of(
            "assessmentEnabled",
            "assessmentNotice",
            "announcementEnabled",
            "announcementTitle",
            "announcementContent"
        )
    ),
    JOB_PROFILE_CREATE("JOB_PROFILE", Set.of("version", "positionId", "definitionJson", "status")),
    JOB_PROFILE_PUBLISH("JOB_PROFILE", Set.of("status")),
    JOB_PROFILE_ARCHIVE("JOB_PROFILE", Set.of("status")),
    TEMPLATE_CREATE(
        "GROWTH_TEMPLATE",
        Set.of(
            "version",
            "title",
            "suggestedCycle",
            "observationTarget",
            "reviewPrompt",
            "fallbackContext",
            "fallbackAction",
            "status"
        )
    ),
    TEMPLATE_PUBLISH("GROWTH_TEMPLATE", Set.of("status")),
    TEMPLATE_ARCHIVE("GROWTH_TEMPLATE", Set.of("status")),
    LEAVE_CREATE("LEAVE", Set.of("leaveType", "startTime", "endTime", "reason", "status")),
    LEAVE_REVIEW("LEAVE", Set.of("status", "reviewComment", "reviewerId", "reviewedAt")),
    SCALE_IMPORT("SCALE", Set.of("definitionJson", "status")),
    SCALE_PUBLISH("SCALE", Set.of("status", "publishedBy", "publishedAt")),
    SCALE_ARCHIVE("SCALE", Set.of("status")),
    GRAPH_IMPORT("GRAPH", Set.of("definitionJson", "status")),
    GRAPH_PUBLISH("GRAPH", Set.of("status")),
    GRAPH_ARCHIVE("GRAPH", Set.of("status")),
    ATTENDANCE_RULE_UPDATE("ATTENDANCE_RULE", Set.of("workStartTime", "workEndTime")),
    DEPARTMENT_SELF_APPROVER("DEPARTMENT", Set.of("selfApproverUserId")),
    DEPARTMENT_UPDATE("DEPARTMENT", Set.of("departmentName", "departmentCode", "sortOrder")),
    POSITION_UPDATE("POSITION", Set.of("positionName", "positionCode", "description")),
    USER_CREATE(
        "USER",
        Set.of(
            "userName",
            "realName",
            "phone",
            "roleCode",
            "departmentId",
            "positionId",
            "auditStatus",
            "employmentStatus"
        )
    ),
    USER_AUDIT("USER", Set.of("auditStatus", "auditReason")),
    USER_ACCESS("USER", Set.of("roleCode", "userStatus")),
    EMPLOYEE_UPDATE(
        "EMPLOYEE",
        Set.of("realName", "phone", "departmentId", "positionId", "hireDate", "employmentStatus")
    ),
    DEPARTMENT_CREATE(
        "DEPARTMENT",
        Set.of("departmentName", "departmentCode", "managerUserId", "sortOrder", "isEnabled")
    ),
    DEPARTMENT_STATUS("DEPARTMENT", Set.of("isEnabled")),
    DEPARTMENT_MANAGER("DEPARTMENT", Set.of("managerUserId")),
    POSITION_CREATE(
        "POSITION",
        Set.of("positionName", "positionCode", "departmentId", "description", "isEnabled")
    ),
    POSITION_STATUS("POSITION", Set.of("isEnabled"));

    private final String targetType;
    private final Set<String> allowedFields;

    AuditOperation(String targetType, Set<String> allowedFields) {
        this.targetType = targetType;
        this.allowedFields = allowedFields;
    }

    public String targetType() {
        return targetType;
    }

    public boolean allows(String field) {
        return allowedFields.contains(field);
    }
}
