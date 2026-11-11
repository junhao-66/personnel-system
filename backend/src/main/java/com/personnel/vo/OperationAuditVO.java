package com.personnel.vo;

import java.time.LocalDateTime;
import java.util.List;

public record OperationAuditVO(
    Long id,
    Long actorUserId,
    String actorRole,
    String operation,
    String targetType,
    Long targetId,
    List<String> changedFields,
    LocalDateTime occurredAt
) {}
